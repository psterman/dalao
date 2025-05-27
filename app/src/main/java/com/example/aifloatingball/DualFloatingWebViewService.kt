package com.example.aifloatingball

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.SearchManager
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.TextUtils
import android.util.Log
import android.util.TypedValue
import android.view.*
import android.view.ActionMode
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.adapter.SearchEngineAdapter
import com.example.aifloatingball.manager.SearchEngineManager
import com.example.aifloatingball.manager.TextSelectionManager
import com.example.aifloatingball.model.SearchEngine
import com.example.aifloatingball.utils.EngineUtil
import com.example.aifloatingball.utils.FaviconManager
import com.example.aifloatingball.utils.IconLoader
import org.json.JSONObject
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.TimeUnit
import kotlin.concurrent.withLock
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import com.example.aifloatingball.model.HandleType
import android.widget.PopupWindow
import android.view.ViewPropertyAnimator
import java.io.File as JavaFile
import com.example.aifloatingball.view.CustomHorizontalScrollbar
import android.util.Pair
import com.example.aifloatingball.utils.WebViewInputHelper
import android.view.KeyEvent
import com.example.aifloatingball.manager.ChatManager
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AlertDialog

class DualFloatingWebViewService : Service() {
    companion object {
        var isRunning = false
        private const val TAG = "DualFloatingWebView"
        private const val PREFS_NAME = "dual_floating_window_prefs"
        private const val KEY_WINDOW_WIDTH = "window_width"
        private const val KEY_WINDOW_HEIGHT = "window_height"
        private const val KEY_WINDOW_X = "window_x"
        private const val KEY_WINDOW_Y = "window_y"
        private const val KEY_IS_HORIZONTAL = "is_horizontal"
        private const val DEFAULT_WIDTH_RATIO = 0.95f
        private const val DEFAULT_HEIGHT_RATIO = 0.7f
        private const val MIN_WIDTH_DP = 300
        private const val MIN_HEIGHT_DP = 400
        private const val WEBVIEW_WIDTH_DP = 300 // 每个WebView的宽度
        
        // 前台服务通知相关常量
        private const val CHANNEL_ID = "dual_webview_channel"
        private const val NOTIFICATION_ID = 1001
        private const val MENU_SHOW_TIMEOUT = 200L
        private const val MENU_AUTO_HIDE_DELAY = 8000L
    }

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private var firstWebView: WebView? = null
    private var secondWebView: WebView? = null
    private var thirdWebView: WebView? = null  // 添加第三个WebView
    private var searchInput: EditText? = null
    private var toggleLayoutButton: ImageButton? = null
    private var closeButton: ImageButton? = null
    private var resizeHandle: View? = null
    private var container: LinearLayout? = null
    private var divider1: View? = null  // 重命名为divider1
    private var divider2: View? = null  // 添加divider2
    private var singleWindowButton: ImageButton? = null
    private var windowCountToggleView: TextView? = null // 添加窗口计数切换按钮
    private var firstTitle: TextView? = null
    private var secondTitle: TextView? = null
    private var thirdTitle: TextView? = null  // 添加第三个标题
    private var firstEngineContainer: LinearLayout? = null
    private var secondEngineContainer: LinearLayout? = null
    private var thirdEngineContainer: LinearLayout? = null  // 添加第三个引擎容器
    private var firstAIEngineContainer: LinearLayout? = null
    private var secondAIEngineContainer: LinearLayout? = null
    private var thirdAIEngineContainer: LinearLayout? = null  // 添加AI引擎容器
    private var firstEngineToggle: ImageButton? = null
    private var secondEngineToggle: ImageButton? = null
    private var thirdEngineToggle: ImageButton? = null
    private var firstAIScrollContainer: HorizontalScrollView? = null
    private var secondAIScrollContainer: HorizontalScrollView? = null
    private var thirdAIScrollContainer: HorizontalScrollView? = null
    private var currentEngineKey: String = "baidu"
    private var currentWebView: WebView? = null
    private var currentTitle: TextView? = null
    private var saveButton: ImageButton? = null // 保存按钮
    private var switchToNormalButton: ImageButton? = null // 添加切换按钮
    
    private lateinit var settingsManager: SettingsManager
    private var leftEngineKey: String = "baidu"
    private var centerEngineKey: String = "bing"   // 添加中间引擎键
    private var rightEngineKey: String = "google"
    private var windowCount: Int = 2 // 默认窗口数量

    private var isHorizontalLayout = true // 默认为水平布局

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var initialWidth = 0
    private var initialHeight = 0
    private var isMoving = false
    private var isResizing = false

    private val sharedPrefs by lazy {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private lateinit var faviconManager: FaviconManager
    private lateinit var iconLoader: IconLoader

    private var textSelectionMenu: PopupWindow? = null
    private var textSelectionManager: TextSelectionManager? = null
    private var currentSelectedText: String = ""

    private val mainHandler = Handler(Looper.getMainLooper())
    
    // 单例模式控制变量
    private val isMenuOperationInProgress = AtomicBoolean(false)
    private val isMenuShowing = AtomicBoolean(false)
    private val isMenuAnimating = AtomicBoolean(false)

    // 当前活跃的WebView引用
    private var currentActiveWebView: WebView? = null
    private var lastTouchDownTime = 0L
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    // 菜单显示锁
    private val menuShowLock = ReentrantLock()
    private val menuAnimationComplete = menuShowLock.newCondition()

    // 全局单例菜单控制
    private object TextSelectionMenuController {
        private var currentMenuView: View? = null
        private var isMenuShowing = AtomicBoolean(false)
        private var isMenuAnimating = AtomicBoolean(false)
        private val menuLock = ReentrantLock()
        private val menuCondition = menuLock.newCondition()
        private var currentWebView: WebView? = null

        fun cleanupState() {
            currentMenuView = null
            isMenuShowing.set(false)
            isMenuAnimating.set(false)
            currentWebView = null
        }

        private fun animateMenuItemAndExecute(view: View, windowManager: WindowManager, action: () -> Unit) {
            view.animate()
                .alpha(0f)
                .scaleX(0.8f)
                .scaleY(0.8f)
                .setDuration(150)
                .setInterpolator(AccelerateInterpolator())
                .withEndAction {
                    try {
                        action.invoke()
                        windowManager.removeView(view)
                        cleanupState()
                    } catch (e: Exception) {
                        Log.e(TAG, "执行菜单命令失败", e)
                    }
                }
                .start()
        }

        private fun executeSelectAll(webView: WebView) {
            webView.evaluateJavascript("""
                (function() {
                    var selection = window.getSelection();
                    selection.removeAllRanges();
                    var range = document.createRange();
                    range.selectNodeContents(document.body);
                    selection.addRange(range);
                    return selection.toString();
                })();
            """.trimIndent()) { result ->
                Log.d(TAG, "全选文本: ${result?.take(50)}...")
            }
        }

        private fun executeCopy(context: Context, webView: WebView) {
            webView.evaluateJavascript("""
                (function() {
                    var selection = window.getSelection();
                    var text = selection.toString();
                    selection.removeAllRanges(); // 清除选择以避免视觉干扰
                    return text;
                })();
            """.trimIndent()) { result ->
                val text = result?.trim('"') ?: ""
                if (text.isNotEmpty()) {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("selected text", text)
                    clipboard.setPrimaryClip(clip)
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                    }
                    Log.d(TAG, "已复制文本: ${text.take(50)}...")
                }
            }
        }

        private fun executeCut(context: Context, webView: WebView) {
            webView.evaluateJavascript("""
                (function() {
                    var selection = window.getSelection();
                    var text = selection.toString();
                    if (text) {
                        try {
                            // 尝试使用execCommand
                            document.execCommand('cut');
                        } catch(e) {
                            // 如果execCommand失败，手动删除选中内容
                            var range = selection.getRangeAt(0);
                            range.deleteContents();
                            selection.removeAllRanges();
                        }
                    }
                    return text;
                })();
            """.trimIndent()) { result ->
                val text = result?.trim('"') ?: ""
                if (text.isNotEmpty()) {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("cut text", text)
                    clipboard.setPrimaryClip(clip)
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(context, "已剪切", Toast.LENGTH_SHORT).show()
                    }
                    Log.d(TAG, "已剪切文本: ${text.take(50)}...")
                }
            }
        }

        private fun executePaste(context: Context, webView: WebView) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            if (clipboard.hasPrimaryClip()) {
                val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                if (text.isNotEmpty()) {
                    // 转义特殊字符
                    val escapedText = text.replace("\\", "\\\\")
                        .replace("'", "\\'")
                        .replace("\n", "\\n")
                        .replace("\r", "\\r")
                        .replace("""$""", """\\$""")
                        .replace("\"", "\\\"")
                    
                    webView.evaluateJavascript("""
                        (function() {
                            var selection = window.getSelection();
                            var range = selection.getRangeAt(0);
                            
                            // 创建文本节点
                            var textNode = document.createTextNode("$escapedText");
                            
                            // 删除当前选中内容
                            range.deleteContents();
                            
                            // 插入新文本
                            range.insertNode(textNode);
                            
                            // 将光标移动到插入文本的末尾
                            range.setStartAfter(textNode);
                            range.setEndAfter(textNode);
                            selection.removeAllRanges();
                            selection.addRange(range);
                            
                            return true;
                        })();
                    """.trimIndent()) { result ->
                        if (result == "true") {
                            Handler(Looper.getMainLooper()).post {
                                Toast.makeText(context, "已粘贴", Toast.LENGTH_SHORT).show()
                            }
                            Log.d(TAG, "已粘贴文本: ${text.take(50)}...")
                        }
                    }
                }
            }
        }

        fun showMenu(
            context: Context,
            windowManager: WindowManager,
            webView: WebView,
            x: Int,
            y: Int,
            onMenuShown: () -> Unit
        ) {
            if (currentWebView != webView || isMenuShowing.get()) {
                hideCurrentMenu(windowManager) {
                    doShowMenu(context, windowManager, webView, x, y, onMenuShown)
                }
            } else {
                doShowMenu(context, windowManager, webView, x, y, onMenuShown)
            }
        }

        private fun doShowMenu(
            context: Context,
            windowManager: WindowManager,
            webView: WebView,
            x: Int,
            y: Int,
            onMenuShown: () -> Unit
        ) {
            menuLock.withLock {
                try {
                    isMenuShowing.set(true)
                    isMenuAnimating.set(true)
                    currentWebView = webView

                    val menuView = LayoutInflater.from(context)
                        .inflate(R.layout.text_selection_menu, null).apply {
                            alpha = 0f
                            scaleX = 0.8f
                            scaleY = 0.8f
                            
                            // 全选按钮
                            findViewById<View>(R.id.action_select_all)?.setOnClickListener {
                                animateMenuItemAndExecute(this, windowManager) {
                                    executeSelectAll(webView)
                                }
                            }

                            // 复制按钮
                            findViewById<View>(R.id.action_copy)?.setOnClickListener {
                                animateMenuItemAndExecute(this, windowManager) {
                                    executeCopy(context, webView)
                                }
                            }

                            // 剪切按钮
                            findViewById<View>(R.id.action_cut)?.setOnClickListener {
                                animateMenuItemAndExecute(this, windowManager) {
                                    executeCut(context, webView)
                                }
                            }

                            // 粘贴按钮
                            findViewById<View>(R.id.action_paste)?.setOnClickListener {
                                animateMenuItemAndExecute(this, windowManager) {
                                    executePaste(context, webView)
                                }
                            }
                        }

                    val params = WindowManager.LayoutParams(
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                        PixelFormat.TRANSLUCENT
                    ).apply {
                        this.x = x
                        this.y = y
                        gravity = Gravity.START or Gravity.TOP
                    }

                    try {
                        windowManager.addView(menuView, params)
                        currentMenuView = menuView

                        menuView.animate()
                            .alpha(1f)
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(200)
                            .setInterpolator(DecelerateInterpolator())
                            .withEndAction {
                                isMenuAnimating.set(false)
                                onMenuShown()
                            }
                            .start()

                    } catch (e: Exception) {
                        Log.e(TAG, "显示菜单失败", e)
                        cleanupState()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "创建菜单失败", e)
                    cleanupState()
                }
            }
        }

        fun hideCurrentMenu(windowManager: WindowManager, callback: (() -> Unit)? = null) {
            if (!isMenuShowing.get() && !isMenuAnimating.get()) {
                callback?.invoke()
                return
            }

            menuLock.withLock {
                try {
                    isMenuAnimating.set(true)
                    currentMenuView?.let { view ->
                        if (view.isAttachedToWindow && view.windowToken != null) {
                            view.animate()
                                .alpha(0f)
                                .scaleX(0.8f)
                                .scaleY(0.8f)
                                .setDuration(150)
                                .setInterpolator(AccelerateInterpolator())
                                .withEndAction {
                                    try {
                                        windowManager.removeView(view)
                                    } catch (e: Exception) {
                                        Log.e(TAG, "移除菜单视图失败", e)
                                    }
                                    cleanupState()
                                    callback?.invoke()
                                }
                                .start()
                        } else {
                            cleanupState()
                            callback?.invoke()
                        }
                    } ?: run {
                        cleanupState()
                        callback?.invoke()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "隐藏菜单失败", e)
                    cleanupState()
                    callback?.invoke()
                }
            }
        }
    }

    /**
     * 扩展函数将dp转换为px
     */
    private fun Int.dpToPx(context: Context): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            this.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private lateinit var clipboardManager: ClipboardManager
    private var isAIEngineActive = false

    override fun onCreate() {
        super.onCreate()
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        isRunning = true
        try {
            // 创建并启动前台服务
            startForeground()
            
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            
            // 初始化设置管理器
            settingsManager = SettingsManager.getInstance(this)
            
            // 初始化Favicon管理器
            faviconManager = FaviconManager.getInstance(this)
            
            // 初始化IconLoader
            iconLoader = IconLoader(this)
            iconLoader.cleanupOldCache()
            
            // 从设置中获取用户设置的窗口数量
            windowCount = settingsManager.getDefaultWindowCount()
            
            // 从设置中获取用户设置的搜索引擎
            leftEngineKey = settingsManager.getLeftWindowSearchEngine()
            centerEngineKey = settingsManager.getCenterWindowSearchEngine()
            rightEngineKey = settingsManager.getRightWindowSearchEngine()
            
            // 创建浮动窗口
            createFloatingView()
            
            // 加载上次的窗口状态
            loadWindowState()
            
            // 初始化WebView
            setupWebViews()
            
            // 设置搜索引擎容器布局
            setupEngineContainers()
            
            // 设置控件事件
            setupControls()
            
            // 初始化搜索引擎图标
            updateEngineIcons()
            
            // 更新所有搜索引擎图标状态
            firstEngineContainer?.let { updateEngineIconStates(it, leftEngineKey, false) }
            secondEngineContainer?.let { updateEngineIconStates(it, centerEngineKey, false) }
            thirdEngineContainer?.let { updateEngineIconStates(it, rightEngineKey, false) }
            
            // 根据窗口数量设置视图可见性
            updateViewVisibilityByWindowCount()
            
            // 刷新引擎切换按钮，确保点击事件被正确设置
            refreshEngineToggleButtons()
            
            // 记录按钮状态
            Log.d(TAG, "服务初始化完成，引擎切换按钮状态：")
            Log.d(TAG, "firstEngineToggle: ${if (firstEngineToggle?.visibility == View.VISIBLE) "可见" else "不可见"}")
            Log.d(TAG, "secondEngineToggle: ${if (secondEngineToggle?.visibility == View.VISIBLE) "可见" else "不可见"}")
            Log.d(TAG, "thirdEngineToggle: ${if (thirdEngineToggle?.visibility == View.VISIBLE) "可见" else "不可见"}")
            
            // 设置引擎切换按钮
            setupEngineToggles()
            
        } catch (e: Exception) {
            Log.e(TAG, "创建服务失败", e)
            stopSelf()
        }
    }

    /**
     * 创建前台服务，避免系统限制服务启动
     */
    private fun startForeground() {
        // 创建通知渠道（Android 8.0+需要）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "浮动窗口服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "多窗口浏览服务，用于支持同时查看多个网页"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
        
        // 创建点击通知时的Intent
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, SettingsActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        
        // 创建通知
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("多窗口浏览器")
            .setContentText("正在运行多窗口浏览服务")
            .setSmallIcon(R.drawable.ic_search)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        
        // 启动前台服务
        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 从Intent中获取窗口数量设置（如果有）
        intent?.getIntExtra("window_count", -1)?.let { count ->
            if (count > 0) {
                // 更新窗口数量
                windowCount = count
                // 根据窗口数量更新视图可见性
                updateViewVisibilityByWindowCount()
            }
        }
        
        // 获取查询参数并执行搜索
        intent?.getStringExtra("search_query")?.let { query ->
            if (query.isNotEmpty()) {
                // 将搜索查询显示在搜索框中
                searchInput?.setText(query)
                
                // 延迟执行搜索，确保WebView已完全初始化
                Handler(Looper.getMainLooper()).postDelayed({
                    performDualSearch()
                }, 500)
            }
        }
        
        // 获取URL参数
        intent?.getStringExtra("url")?.let { url ->
            if (url.isNotEmpty()) {
                // 加载URL到第一个WebView
                firstWebView?.let { webView ->
                    webViewInputHelper.prepareWebViewForInput(webView)
                    webView.loadUrl(url)
                }
                
                // 根据URL确定搜索引擎
                val isGoogle = url.contains("google.com")
                val isBaidu = url.contains("baidu.com")
                
                // 如果是百度或谷歌，加载另一个搜索引擎
                if (windowCount >= 2) {
                    secondWebView?.let { webView ->
                        webViewInputHelper.prepareWebViewForInput(webView)
                        if (isGoogle) {
                            webView.loadUrl("https://www.baidu.com")
                        } else {
                            webView.loadUrl("https://www.google.com")
                        }
                    }
                }
                
                // 如果是三窗口模式，加载第三个窗口
                if (windowCount >= 3) {
                    thirdWebView?.let { webView ->
                        webViewInputHelper.prepareWebViewForInput(webView)
                        if (isGoogle || isBaidu) {
                            webView.loadUrl("https://www.bing.com")
                        } else {
                            webView.loadUrl("https://www.zhihu.com")
                        }
                    }
                }
            }
        }
        
        return START_NOT_STICKY
    }

    private fun createFloatingView() {
        try {
            // 加载悬浮窗布局
            floatingView = LayoutInflater.from(this).inflate(R.layout.layout_dual_floating_webview, null)
            
            // 初始化视图组件
            initializeViews()
            
            // 创建窗口参数
            val params = createWindowLayoutParams()
            
            // 添加到窗口
            windowManager.addView(floatingView, params)
            
            // 初始化设置
            initializeSettings()
            
            // 设置主容器的触摸事件分发
            setupMainContainerTouchEvents()
            
            // 获取用户设置的默认窗口数量
            val windowCount = settingsManager.getDefaultWindowCount()
            
            // 根据窗口数量显示或隐藏相应的窗口
            updateWindowVisibility(windowCount)
            
        } catch (e: Exception) {
            Log.e(TAG, "创建悬浮窗失败", e)
            stopSelf()
        }
    }
    
    /**
     * 设置主容器的触摸事件分发
     * 区分WebView区域和上方区域的触摸事件
     */
    private fun setupMainContainerTouchEvents() {
        try {
            // 获取WebView容器和WebView
            val webViewContainer = floatingView?.findViewById<View>(R.id.dual_webview_container)
            val webViews = listOfNotNull(firstWebView, secondWebView, thirdWebView)
            
            // 获取WebView相对于容器的位置信息
            val container = webViewContainer
            container?.post {
                // 监听WebView容器的触摸事件
                container.setOnTouchListener { _, event ->
                    try {
                        // 检查触摸点是否位于WebView区域
                        var isInWebViewArea = false
                        
                        // 遍历所有WebView检查触摸点位置
                        for (view in webViews) {
                            val location = IntArray(2)
                            view.getLocationOnScreen(location)
                            
                            val webViewRect = Rect().apply {
                                left = location[0]
                                top = location[1]
                                right = left + view.width
                                bottom = top + view.height
                            }
                            
                            // 检查触摸点是否在WebView区域内
                            if (webViewRect.contains(event.rawX.toInt(), event.rawY.toInt())) {
                                isInWebViewArea = true
                                break
                            }
                        }
                        
                        // 根据触摸区域决定是否允许事件传递
                        if (!isInWebViewArea) {
                            // 如果不在WebView区域，阻止横向滑动事件传递
                            when (event.action) {
                                MotionEvent.ACTION_DOWN -> {
                                    // 记录初始触摸位置
                                    initialTouchX = event.x
                                    initialTouchY = event.y
                                }
                                MotionEvent.ACTION_MOVE -> {
                                    // 计算横向移动距离
                                    val deltaX = Math.abs(event.x - initialTouchX)
                                    val deltaY = Math.abs(event.y - initialTouchY)
                                    
                                    // 如果是横向滑动，拦截事件
                                    if (deltaX > deltaY && deltaX > ViewConfiguration.get(this).scaledTouchSlop) {
                                        return@setOnTouchListener true
                                    }
                                }
                            }
                        }
                        
                        // 对于在WebView区域内的触摸或非横向滑动，允许事件传递
                        false
                    } catch (e: Exception) {
                        Log.e(TAG, "处理触摸事件失败", e)
                        false
                    }
                }
            }
            
            Log.d(TAG, "已设置主容器的触摸事件分发")
        } catch (e: Exception) {
            Log.e(TAG, "设置主容器触摸事件失败", e)
        }
    }

    private fun initializeViews() {
        // 初始化视图引用
        container = floatingView?.findViewById(R.id.dual_webview_container)
        firstWebView = floatingView?.findViewById(R.id.first_floating_webview)
        secondWebView = floatingView?.findViewById(R.id.second_floating_webview)
        thirdWebView = floatingView?.findViewById(R.id.third_floating_webview)  // 添加第三个WebView
        searchInput = floatingView?.findViewById(R.id.dual_search_input)
        toggleLayoutButton = floatingView?.findViewById(R.id.btn_toggle_layout)
        // 移除dualSearchButton赋值
        closeButton = floatingView?.findViewById(R.id.btn_dual_close)
        resizeHandle = floatingView?.findViewById(R.id.dual_resize_handle)
        divider1 = floatingView?.findViewById(R.id.divider1)  // 更新divider1
        divider2 = floatingView?.findViewById(R.id.divider2)  // 添加divider2
        singleWindowButton = floatingView?.findViewById(R.id.btn_single_window)
        windowCountToggleView = floatingView?.findViewById(R.id.window_count_toggle) // 初始化窗口计数切换视图
        firstTitle = floatingView?.findViewById(R.id.first_floating_title)
        secondTitle = floatingView?.findViewById(R.id.second_floating_title)
        thirdTitle = floatingView?.findViewById(R.id.third_floating_title)  // 添加第三个标题
        firstEngineContainer = floatingView?.findViewById(R.id.first_engine_container)
        secondEngineContainer = floatingView?.findViewById(R.id.second_engine_container)
        thirdEngineContainer = floatingView?.findViewById(R.id.third_engine_container)  // 添加第三个引擎容器
        firstAIEngineContainer = floatingView?.findViewById(R.id.first_ai_engine_container)
        secondAIEngineContainer = floatingView?.findViewById(R.id.second_ai_engine_container)
        thirdAIEngineContainer = floatingView?.findViewById(R.id.third_ai_engine_container)  // 添加AI引擎容器
        firstEngineToggle = floatingView?.findViewById(R.id.first_engine_toggle)
        secondEngineToggle = floatingView?.findViewById(R.id.second_engine_toggle)
        thirdEngineToggle = floatingView?.findViewById(R.id.third_engine_toggle)
        firstAIScrollContainer = floatingView?.findViewById(R.id.first_ai_scroll_container)
        secondAIScrollContainer = floatingView?.findViewById(R.id.second_ai_scroll_container)
        thirdAIScrollContainer = floatingView?.findViewById(R.id.third_ai_scroll_container)
        saveButton = floatingView?.findViewById(R.id.btn_save_engines) // 保存按钮
        
        // 使用动态方法查找切换按钮，避免编译错误
        val btnSwitchId = resources.getIdentifier("btn_switch_normal", "id", packageName)
        if (btnSwitchId != 0) {
            switchToNormalButton = floatingView?.findViewById(btnSwitchId)
            Log.d(TAG, "通过资源ID查找到切换按钮: $btnSwitchId")
        } else {
            // 如果没有找到ID，尝试其他可能的ID
            val alternativeIds = arrayOf(
                "btn_switch", 
                "btn_switch_mode", 
                "btn_mode"
            )
            
            for (alternativeId in alternativeIds) {
                val id = resources.getIdentifier(alternativeId, "id", packageName)
                if (id != 0) {
                    switchToNormalButton = floatingView?.findViewById(id)
                    if (switchToNormalButton != null) {
                        Log.d(TAG, "通过替代ID找到切换按钮: $alternativeId")
                        break
                    }
                }
            }
        }
        
        // 如果仍未找到，尝试动态创建按钮
        if (switchToNormalButton == null) {
            Log.e(TAG, "无法找到切换按钮，尝试动态创建")
            
            // 查找顶部工具栏
            val topBar = floatingView?.findViewById<ViewGroup>(R.id.top_control_bar)
            if (topBar != null) {
                // 创建新的按钮
                val newButton = ImageButton(this).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    setImageResource(R.drawable.ic_switch_mode)
                    background = getDrawable(R.drawable.circle_ripple)
                    contentDescription = "切换到普通模式"
                    setPadding(8, 8, 8, 8)
                }
                
                // 添加到工具栏
                topBar.addView(newButton)
                switchToNormalButton = newButton
                Log.d(TAG, "已动态创建并添加切换按钮")
            } else {
                Log.e(TAG, "无法找到顶部工具栏，切换按钮将无法使用")
            }
        }
        
        // 添加调试日志
        Log.d(TAG, "初始化视图完成:")
        Log.d(TAG, "firstEngineToggle: $firstEngineToggle")
        Log.d(TAG, "secondEngineToggle: $secondEngineToggle")
        Log.d(TAG, "thirdEngineToggle: $thirdEngineToggle")
        Log.d(TAG, "firstAIScrollContainer: $firstAIScrollContainer")
        Log.d(TAG, "secondAIScrollContainer: $secondAIScrollContainer")
        Log.d(TAG, "thirdAIScrollContainer: $thirdAIScrollContainer")
    }

    /**
     * 创建窗口布局参数
     */
    private fun createWindowLayoutParams(): WindowManager.LayoutParams {
        // 获取屏幕尺寸
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        val defaultWidth = (screenWidth * DEFAULT_WIDTH_RATIO).toInt()
        val defaultHeight = (screenHeight * DEFAULT_HEIGHT_RATIO).toInt()
        
        // FLAG_NOT_FOCUSABLE参数允许窗口与下层窗口交互，但需要在输入时切换为可获取焦点
        // FLAG_ALT_FOCUSABLE_IM参数防止输入法遮挡，在需要输入时将其移除
        // 其他参数设置悬浮窗行为
        val params = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams(
                defaultWidth,
                defaultHeight,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )
        } else {
            WindowManager.LayoutParams(
                defaultWidth,
                defaultHeight,
                WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )
        }
        
        params.gravity = Gravity.CENTER
        params.windowAnimations = android.R.style.Animation_Dialog
        
        // 启用接收触摸事件的标志
        params.flags = params.flags or WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR
        
        return params
    }

    private fun saveWindowState() {
        val params = floatingView?.layoutParams as? WindowManager.LayoutParams ?: return
        sharedPrefs.edit().apply {
            putInt(KEY_WINDOW_WIDTH, params.width)
            putInt(KEY_WINDOW_HEIGHT, params.height)
            putInt(KEY_WINDOW_X, params.x)
            putInt(KEY_WINDOW_Y, params.y)
            putBoolean(KEY_IS_HORIZONTAL, isHorizontalLayout)
            apply()
        }
    }

    private fun loadWindowState() {
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        val defaultWidth = (screenWidth * DEFAULT_WIDTH_RATIO).toInt()
        val defaultHeight = (screenHeight * DEFAULT_HEIGHT_RATIO).toInt()
        
        val params = floatingView?.layoutParams as? WindowManager.LayoutParams ?: return
        
        params.width = sharedPrefs.getInt(KEY_WINDOW_WIDTH, defaultWidth)
        params.height = sharedPrefs.getInt(KEY_WINDOW_HEIGHT, defaultHeight)
        params.x = sharedPrefs.getInt(KEY_WINDOW_X, 0)
        params.y = sharedPrefs.getInt(KEY_WINDOW_Y, 0)
        isHorizontalLayout = sharedPrefs.getBoolean(KEY_IS_HORIZONTAL, true)
        
        // 应用布局方向
        updateLayoutOrientation()
        
        try {
            windowManager.updateViewLayout(floatingView, params)
        } catch (e: Exception) {
            Log.e(TAG, "恢复窗口状态失败", e)
        }
    }

    private lateinit var webViewInputHelper: WebViewInputHelper
    
    /**
     * 设置搜索引擎容器的布局
     */
    private fun setupEngineContainers() {
        try {
            // 创建第一个窗口的搜索引擎容器布局
            val firstEngineLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                
                // 添加普通搜索引擎容器
                firstEngineContainer?.let { addView(it) }
                
                // 添加分隔线
                addView(View(this@DualFloatingWebViewService).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        1.dpToPx(this@DualFloatingWebViewService)
                    )
                    setBackgroundColor(ContextCompat.getColor(this@DualFloatingWebViewService, R.color.divider_color))
                })
                
                // 添加AI搜索引擎容器
                firstAIScrollContainer?.let { addView(it) }
            }
            
            // 创建第二个窗口的搜索引擎容器布局
            val secondEngineLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                // 添加普通搜索引擎容器
                secondEngineContainer?.let { addView(it) }
                
                // 添加分隔线
                addView(View(this@DualFloatingWebViewService).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        1.dpToPx(this@DualFloatingWebViewService)
                    )
                    setBackgroundColor(ContextCompat.getColor(this@DualFloatingWebViewService, R.color.divider_color))
                })
                
                // 添加AI搜索引擎容器
                secondAIScrollContainer?.let { addView(it) }
            }
            
            // 创建第三个窗口的搜索引擎容器布局
            val thirdEngineLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                
                // 添加普通搜索引擎容器
                thirdEngineContainer?.let { addView(it) }
                
                // 添加分隔线
                addView(View(this@DualFloatingWebViewService).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        1.dpToPx(this@DualFloatingWebViewService)
                    )
                    setBackgroundColor(ContextCompat.getColor(this@DualFloatingWebViewService, R.color.divider_color))
                })
                
                // 添加AI搜索引擎容器
                thirdAIScrollContainer?.let { addView(it) }
            }
            
            // 清空WebView容器
            container?.removeAllViews()
            
            // 根据窗口数量添加需要的容器
            val webviewContainer = container?.findViewById<LinearLayout>(R.id.dual_webview_container)
            if (webviewContainer != null) {
                // 移除所有多余的子视图（保留WebView及分割线）
                for (i in webviewContainer.childCount - 1 downTo 0) {
                    val childView = webviewContainer.getChildAt(i)
                    if (childView != firstWebView?.parent && 
                        childView != secondWebView?.parent &&
                        childView != thirdWebView?.parent &&
                        childView != divider1 &&
                        childView != divider2) {
                        webviewContainer.removeViewAt(i)
                    }
                }
            }
            
            // 将布局添加到主容器中
            container?.apply {
                addView(firstEngineLayout)
                if (windowCount >= 2) {
                    addView(secondEngineLayout)
                }
                if (windowCount >= 3) {
                    addView(thirdEngineLayout)
                }
            }
            
            // 修改初始状态设置 - 两种搜索引擎都显示
            firstAIScrollContainer?.visibility = View.VISIBLE
            secondAIScrollContainer?.visibility = if (windowCount >= 2) View.VISIBLE else View.GONE
            thirdAIScrollContainer?.visibility = if (windowCount >= 3) View.VISIBLE else View.GONE
            
            // 确保AI搜索引擎按钮已创建
            firstWebView?.let { webView ->
                firstAIEngineContainer?.let { container ->
                    if (container.childCount == 0) {
                        createAIEngineButtons(webView, container as LinearLayout, "left") { key ->
                            leftEngineKey = key
                            settingsManager.setLeftWindowSearchEngine(key)
                        }
                    }
                }
            }
            
            if (windowCount >= 2) {
                secondWebView?.let { webView ->
                    secondAIEngineContainer?.let { container ->
                        if (container.childCount == 0) {
                            createAIEngineButtons(webView, container as LinearLayout, "center") { key ->
                                centerEngineKey = key
                                settingsManager.setCenterWindowSearchEngine(key)
                            }
                        }
                    }
                }
            }
            
            if (windowCount >= 3) {
                thirdWebView?.let { webView ->
                    thirdAIEngineContainer?.let { container ->
                        if (container.childCount == 0) {
                            createAIEngineButtons(webView, container as LinearLayout, "right") { key ->
                                rightEngineKey = key
                                settingsManager.setRightWindowSearchEngine(key)
                            }
                        }
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "设置搜索引擎容器布局失败", e)
        }
    }
    
    private fun setupWebViews() {
        // 初始化WebView输入辅助类
        webViewInputHelper = WebViewInputHelper(this, windowManager, floatingView)
        
        // 创建搜索引擎容器布局
        val engineContainerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        
        // 初始化AI搜索引擎容器
        firstAIEngineContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(8.dpToPx(this@DualFloatingWebViewService), 
                      4.dpToPx(this@DualFloatingWebViewService),
                      8.dpToPx(this@DualFloatingWebViewService),
                      4.dpToPx(this@DualFloatingWebViewService))
        }
        
        secondAIEngineContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        
        thirdAIEngineContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        
        // 初始化AI搜索引擎滚动容器
        firstAIScrollContainer = HorizontalScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            isHorizontalScrollBarEnabled = true
            addView(firstAIEngineContainer)
            visibility = View.GONE
        }
        
        secondAIScrollContainer = HorizontalScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            isHorizontalScrollBarEnabled = true
            addView(secondAIEngineContainer)
            visibility = View.GONE
        }
        
        thirdAIScrollContainer = HorizontalScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            isHorizontalScrollBarEnabled = true
            addView(thirdAIEngineContainer)
            visibility = View.GONE
        }
        
        // 将AI搜索引擎容器添加到布局中
        container?.apply {
            addView(firstAIScrollContainer)
            addView(secondAIScrollContainer)
            addView(thirdAIScrollContainer)
        }
        
        val webViews = listOfNotNull(firstWebView, secondWebView, thirdWebView)
        
        webViews.forEach { webView ->
            // 使用辅助类设置WebView
            webViewInputHelper.prepareWebViewForInput(webView)

            // 添加输入法桥接接口
            webView.addJavascriptInterface(object {
                @JavascriptInterface
                fun onInputFieldFocused() {
                    Handler(Looper.getMainLooper()).post {
                        webViewInputHelper.ensureInputMethodAvailable(webView)
                    }
                }

                @JavascriptInterface
                fun onInputFieldClicked() {
                    Handler(Looper.getMainLooper()).post {
                        webViewInputHelper.forceShowInputMethod(webView)
                    }
                }
            }, "InputMethodBridge")

            // 添加原有的JSBridge
            webView.addJavascriptInterface(WebViewJSBridge(webView), "NativeBridge")
            
            // 设置WebViewClient
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    webViewInputHelper.injectInputMethodSupport(webView)
                }
            }
            
            // 设置触摸事件处理
            webView.setOnLongClickListener { view ->
                // 获取长按位置的HitTestResult
                val result = webView.hitTestResult
                
                // 如果是链接类型，显示链接菜单
                if (result.type == WebView.HitTestResult.SRC_ANCHOR_TYPE ||
                    result.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
                    showLinkActionMenu(webView, result.extra ?: "", result.type)
                    true
                } else {
                    false
                }
            }
            
            // 设置触摸事件处理
            webView.setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        lastTouchDownTime = SystemClock.uptimeMillis()
                        lastTouchX = event.x
                        lastTouchY = event.y
                        
                        // 如果点击了不同的WebView，清理之前的选择
                        if (currentActiveWebView != webView) {
                            clearTextSelection()
                            currentActiveWebView = webView
                        }
                        
                        // 不消费事件，让WebView处理
                        v.parent.requestDisallowInterceptTouchEvent(false)
                    }
                    MotionEvent.ACTION_MOVE -> {
                        // 计算水平和垂直移动距离
                        val deltaX = Math.abs(event.x - lastTouchX)
                        val deltaY = Math.abs(event.y - lastTouchY)
                        
                        // 如果水平移动明显大于垂直移动，让父容器处理
                        if (deltaX > deltaY * 1.5f) {
                            v.parent.requestDisallowInterceptTouchEvent(false)
                        } else {
                            // 否则WebView处理(垂直滚动)
                            v.parent.requestDisallowInterceptTouchEvent(true)
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        val touchDuration = SystemClock.uptimeMillis() - lastTouchDownTime
                        val touchMoved = Math.abs(event.x - lastTouchX) > 10 || 
                                       Math.abs(event.y - lastTouchY) > 10
                        
                        if (!touchMoved && touchDuration >= ViewConfiguration.getLongPressTimeout()) {
                            // 长按非链接区域，处理文本选择
                            handleLongPress(webView, event)
                        }
                        
                        // 恢复父视图拦截
                        v.parent.requestDisallowInterceptTouchEvent(false)
                    }
                }
                false // 不消费事件，让WebView处理点击
            }

            // 设置页面加载完成的处理
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    view?.let { 
                        enableTextSelectionMode(it)
                        injectCustomMenuCode(it)
                        injectLinkContextMenuCode(it)
                        webViewInputHelper.injectInputMethodSupport(it)
                    }
                    if (isAIEngineActive && view == currentWebView) {
                        // 页面加载完成后自动填充并发送
                        autoFillAndSendClipboardContent(webView)
                    }
                }
            }
        }
    }

    /**
     * 显示链接操作菜单
     */
    private fun showLinkActionMenu(webView: WebView, url: String, hitType: Int) {
        try {
            // 创建弹出菜单视图
            val menuView = LayoutInflater.from(this).inflate(R.layout.popup_link_menu, null)
            val popupWindow = PopupWindow(
                menuView,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
            ).apply {
                elevation = 24f
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                isOutsideTouchable = true
            }
            
            // 获取链接文本（仅当类型为锚点时）
            if (hitType == WebView.HitTestResult.SRC_ANCHOR_TYPE || 
                hitType == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
                webView.evaluateJavascript("""
                    (function() {
                        var links = document.querySelectorAll('a');
                        for(var i = 0; i < links.length; i++) {
                            if(links[i].href === "$url") {
                                return links[i].innerText || links[i].textContent || '';
                            }
                        }
                        return '';
                    })();
                """.trimIndent()) { result ->
                    val linkText = result.trim('"')
                    setupLinkMenuButtons(menuView, webView, url, linkText)
                }
            } else {
                // 如果只是图片，没有链接文本
                setupLinkMenuButtons(menuView, webView, url, "")
            }
            
            // 在点击位置附近显示菜单
            val location = IntArray(2)
            webView.getLocationOnScreen(location)
            
            // 显示在合适的位置，防止超出屏幕
            val displayMetrics = resources.displayMetrics
            val x = Math.min(location[0] + lastTouchX.toInt(), 
                            displayMetrics.widthPixels - menuView.measuredWidth)
            val y = Math.min(location[1] + lastTouchY.toInt(), 
                            displayMetrics.heightPixels - menuView.measuredHeight)
            
            // 测量视图大小
            menuView.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            
            // 添加进入动画
            menuView.alpha = 0f
            menuView.scaleX = 0.8f
            menuView.scaleY = 0.8f
            
            popupWindow.showAtLocation(webView, Gravity.NO_GRAVITY, x, y)
            
            menuView.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(200)
                .setInterpolator(DecelerateInterpolator())
                .start()
            
        } catch (e: Exception) {
            Log.e(TAG, "显示链接菜单失败", e)
            Toast.makeText(this, "显示菜单失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 设置链接菜单按钮
     */
    private fun setupLinkMenuButtons(
        menuView: View, 
        webView: WebView, 
        url: String, 
        linkText: String
    ) {
        try {
            // 后台打开
            menuView.findViewById<View>(R.id.btn_open_background)?.setOnClickListener {
                openLinkInBackground(url)
                dismissPopupMenu(menuView)
            }
            
            // 新标签打开
            menuView.findViewById<View>(R.id.btn_open_new_tab)?.setOnClickListener {
                openLinkInNewTab(url)
                dismissPopupMenu(menuView)
            }
            
            // 复制链接文字
            val btnCopyText = menuView.findViewById<View>(R.id.btn_copy_text)
            if (linkText.isNotEmpty()) {
                btnCopyText?.setOnClickListener {
                    copyTextToClipboard(linkText)
                    Toast.makeText(this, "已复制链接文字", Toast.LENGTH_SHORT).show()
                    dismissPopupMenu(menuView)
                }
            } else {
                btnCopyText?.visibility = View.GONE
            }
            
            // 复制链接
            menuView.findViewById<View>(R.id.btn_copy_link)?.setOnClickListener {
                copyTextToClipboard(url)
                Toast.makeText(this, "已复制链接", Toast.LENGTH_SHORT).show()
                dismissPopupMenu(menuView)
            }
            
            // 分享链接
            menuView.findViewById<View>(R.id.btn_share_link)?.setOnClickListener {
                shareLinkUrl(url)
                dismissPopupMenu(menuView)
            }
            
            // 生成二维码
            menuView.findViewById<View>(R.id.btn_generate_qr)?.setOnClickListener {
                generateQRCode(url)
                dismissPopupMenu(menuView)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "设置链接菜单按钮失败", e)
        }
    }
    
    /**
     * 关闭弹出菜单
     */
    private fun dismissPopupMenu(menuView: View) {
        try {
            val popupWindow = menuView.parent?.parent as? PopupWindow
            
            // 添加退出动画
            menuView.animate()
                .alpha(0f)
                .scaleX(0.8f)
                .scaleY(0.8f)
                .setDuration(150)
                .setInterpolator(AccelerateInterpolator())
                .withEndAction {
                    popupWindow?.dismiss()
                }
                .start()
                
        } catch (e: Exception) {
            Log.e(TAG, "关闭菜单失败", e)
            // 如果动画失败，直接关闭
            try {
                val popupWindow = menuView.parent?.parent as? PopupWindow
                popupWindow?.dismiss()
            } catch (e2: Exception) {
                Log.e(TAG, "直接关闭菜单也失败", e2)
            }
        }
    }
    
    /**
     * 在后台打开链接
     */
    private fun openLinkInBackground(url: String) {
        try {
            // 根据当前窗口数量选择打开方式
            when (windowCount) {
                1 -> {
                    // 如果当前只有一个窗口，增加到两个窗口并在第二个窗口打开
                    updateWindowVisibility(2)
                    secondWebView?.loadUrl(url)
                    Toast.makeText(this, "已在第二个窗口打开链接", Toast.LENGTH_SHORT).show()
                }
                2 -> {
                    // 如果当前有两个窗口，增加到三个窗口并在第三个窗口打开
                    updateWindowVisibility(3)
                    thirdWebView?.loadUrl(url)
                    Toast.makeText(this, "已在第三个窗口打开链接", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    // 如果已经有三个窗口，在第二个窗口打开
                    secondWebView?.loadUrl(url)
                    Toast.makeText(this, "已在第二个窗口打开链接", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "后台打开链接失败", e)
            Toast.makeText(this, "打开链接失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 在新标签打开链接
     */
    private fun openLinkInNewTab(url: String) {
        try {
            // 创建Intent打开系统浏览器
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "新标签打开链接失败", e)
            Toast.makeText(this, "打开链接失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 复制文本到剪贴板
     */
    private fun copyTextToClipboard(text: String) {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("链接文本", text)
            clipboard.setPrimaryClip(clip)
        } catch (e: Exception) {
            Log.e(TAG, "复制到剪贴板失败", e)
            Toast.makeText(this, "复制失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 分享链接URL
     */
    private fun shareLinkUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, url)
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(Intent.createChooser(intent, "分享链接").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Log.e(TAG, "分享链接失败", e)
            Toast.makeText(this, "分享失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 生成二维码
     */
    private fun generateQRCode(url: String) {
        try {
            // 此处调用二维码生成库生成二维码
            // 目前仅显示提示信息，实际功能需要集成二维码生成库
            Toast.makeText(this, "二维码生成功能将在后续版本添加", Toast.LENGTH_SHORT).show()
            
            // 使用系统二维码扫描应用
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse("https://api.qrserver.com/v1/create-qr-code/?size=300x300&data=" + Uri.encode(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "生成二维码失败", e)
            Toast.makeText(this, "生成二维码失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 注入链接上下文菜单代码
     */
    private fun injectLinkContextMenuCode(webView: WebView) {
        val script = """
            (function() {
                // 禁用默认上下文菜单
                document.addEventListener('contextmenu', function(e) {
                    // 检查是否是链接
                    var element = e.target;
                    var isLink = false;
                    
                    // 向上查找最近的链接元素
                    while (element && element !== document) {
                        if (element.tagName === 'A' && element.href) {
                            isLink = true;
                            break;
                        }
                        element = element.parentElement;
                    }
                    
                    // 如果是链接，阻止默认事件，让长按处理
                    if (isLink) {
                        e.preventDefault();
                        return false;
                    }
                }, false);
                
                console.log('链接上下文菜单代码已注入');
            })();
        """.trimIndent()
        
        webView.evaluateJavascript(script, null)
    }

    private fun injectCustomMenuCode(webView: WebView) {
        val css = """
            #custom_selection_menu {
                position: fixed;
                background: #ffffff;
                border-radius: 6px;
                box-shadow: 0 2px 8px rgba(0,0,0,0.15);
                padding: 4px;
                display: none;
                z-index: 999999;
                font-family: system-ui, -apple-system, sans-serif;
                user-select: none;
                -webkit-user-select: none;
                display: flex;
                flex-direction: row;
                align-items: center;
                justify-content: center;
                min-width: 0;
                max-width: 100%;
                transform-origin: top center;
            }
            #custom_selection_menu button {
                background: none;
                border: none;
                padding: 6px 8px;
                margin: 0 2px;
                color: #333;
                font-size: 12px;
                cursor: pointer;
                border-radius: 4px;
                transition: all 0.2s;
                position: relative;
                overflow: hidden;
                white-space: nowrap;
                flex: 1;
                min-width: 0;
                display: flex;
                align-items: center;
                justify-content: center;
                gap: 4px;
            }
            #custom_selection_menu button:hover {
                background: rgba(0, 0, 0, 0.05);
            }
            #custom_selection_menu button:active {
                background: rgba(0, 0, 0, 0.1);
                transform: scale(0.95);
            }
            #custom_selection_menu button i {
                font-size: 16px;
                width: 16px;
                height: 16px;
                display: flex;
                align-items: center;
                justify-content: center;
                color: #666;
            }
            #custom_selection_menu .divider {
                display: inline-block;
                width: 1px;
                height: 16px;
                background: #e0e0e0;
                margin: 0 2px;
                flex: none;
            }
            @media screen and (max-width: 300px) {
                #custom_selection_menu {
                    flex-wrap: wrap;
                    padding: 2px;
                }
                #custom_selection_menu button {
                    font-size: 11px;
                    padding: 4px 6px;
                }
                #custom_selection_menu button i {
                    font-size: 14px;
                    width: 14px;
                    height: 14px;
                }
                #custom_selection_menu .divider {
                    display: none;
                }
            }
            /* 图标样式 */
            .menu-icon {
                display: inline-block;
                width: 16px;
                height: 16px;
                stroke-width: 2;
                stroke: currentColor;
                fill: none;
                stroke-linecap: round;
                stroke-linejoin: round;
            }
        """.trimIndent()

        val copyIcon = """<svg class="menu-icon" viewBox="0 0 24 24"><path d="M8 4v12a2 2 0 0 0 2 2h8a2 2 0 0 0 2-2V7.242a2 2 0 0 0-.602-1.43L16.083 2.57A2 2 0 0 0 14.685 2H10a2 2 0 0 0-2 2z"/><path d="M16 18v2a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2h2"/></svg>"""
        val shareIcon = """<svg class="menu-icon" viewBox="0 0 24 24"><path d="M4 12v8a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-8"/><polyline points="16 6 12 2 8 6"/><line x1="12" y1="2" x2="12" y2="15"/></svg>"""
        val searchIcon = """<svg class="menu-icon" viewBox="0 0 24 24"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>"""
        val translateIcon = """<svg class="menu-icon" viewBox="0 0 24 24"><path d="M2 5h8"/><path d="M6 5v8"/><path d="M2 9h8"/><path d="M12 13h10"/><path d="M12 17h10"/><path d="M2 13h6"/><path d="M2 17h6"/><path d="M14 5l6 6"/><path d="M20 5l-6 6"/></svg>"""

        val js = """
            (function() {
                // 移除已存在的菜单（如果有）
                var existingMenu = document.getElementById('custom_selection_menu');
                if (existingMenu) {
                    existingMenu.remove();
                }

                // 创建样式
                var style = document.createElement('style');
                style.textContent = `$css`;
                document.head.appendChild(style);

                // 创建菜单
                var menu = document.createElement('div');
                menu.id = 'custom_selection_menu';
                menu.innerHTML = `
                    <button data-action="copy">$copyIcon<span>复制</span></button>
                    <span class="divider"></span>
                    <button data-action="share">$shareIcon<span>分享</span></button>
                    <span class="divider"></span>
                    <button data-action="search">$searchIcon<span>搜索</span></button>
                    <span class="divider"></span>
                    <button data-action="translate">$translateIcon<span>翻译</span></button>
                `;
                document.body.appendChild(menu);

                // 处理菜单点击
                menu.addEventListener('click', function(e) {
                    var button = e.target.closest('button');
                    if (!button) return;
                    
                    e.preventDefault();
                    e.stopPropagation();
                    
                    var action = button.dataset.action;
                    if (!action) return;
                    
                    var selection = window.getSelection();
                    var text = selection.toString().trim();
                    if (!text) return;
                    
                    // 添加点击反馈
                    button.style.transform = 'scale(0.95)';
                    setTimeout(() => {
                        button.style.transform = 'scale(1)';
                    }, 200);

                    try {
                        // 确保 NativeBridge 存在
                        if (!window.NativeBridge) {
                            console.error('NativeBridge not found');
                            return;
                        }

                        // 调用对应的原生方法
                        switch(action) {
                            case 'copy':
                                window.NativeBridge.onCopyText(text);
                                break;
                            case 'share':
                                window.NativeBridge.onShareText(text);
                                break;
                            case 'search':
                                window.NativeBridge.onSearchText(text);
                                break;
                            case 'translate':
                                window.NativeBridge.onTranslateText(text);
                                break;
                        }
                        
                        // 隐藏菜单
                        hideCustomMenu();
                        
                        // 清除选择
                        selection.removeAllRanges();
                    } catch (error) {
                        console.error('Error executing action:', error);
                    }
                });

                // 显示菜单
                window.showCustomMenu = function() {
                    var selection = window.getSelection();
                    if (!selection.toString().trim()) return;

                    var menu = document.getElementById('custom_selection_menu');
                    if (!menu) return;

                    var range = selection.getRangeAt(0);
                    var rect = range.getBoundingClientRect();
                    
                    menu.style.display = 'flex';
                    
                    // 计算菜单位置，优先显示在选中文本的正上方或正下方
                    var menuRect = menu.getBoundingClientRect();
                    var viewportWidth = window.innerWidth;
                    var viewportHeight = window.innerHeight;
                    
                    // 水平居中对齐选中文本
                    var left = rect.left + (rect.width - menuRect.width) / 2;
                    
                    // 确保不超出左右边界
                    left = Math.max(5, Math.min(left, viewportWidth - menuRect.width - 5));
                    
                    // 垂直位置：优先显示在上方
                    var top = rect.top - menuRect.height - 5;
                    if (top < 5) { // 如果上方空间不足，显示在下方
                        top = rect.bottom + 5;
                    }
                    
                    menu.style.left = left + 'px';
                    menu.style.top = top + 'px';
                    
                    // 添加显示动画
                    menu.style.transform = 'scale(0.9)';
                    menu.style.opacity = '0';
                    
                    requestAnimationFrame(() => {
                        menu.style.transition = 'transform 0.2s ease-out, opacity 0.2s ease-out';
                        menu.style.transform = 'scale(1)';
                        menu.style.opacity = '1';
                    });
                };

                // 隐藏菜单
                window.hideCustomMenu = function() {
                    var menu = document.getElementById('custom_selection_menu');
                    if (menu) {
                        menu.style.transform = 'scale(0.9)';
                        menu.style.opacity = '0';
                        setTimeout(() => {
                            menu.style.display = 'none';
                        }, 200);
                    }
                };

                // 监听选择事件
                document.addEventListener('selectionchange', function() {
                    requestAnimationFrame(function() {
                        var selection = window.getSelection();
                        var text = selection.toString().trim();
                        
                        if (text.length > 0) {
                            showCustomMenu();
                        } else {
                            hideCustomMenu();
                        }
                    });
                });

                // 监听触摸结束事件
                document.addEventListener('touchend', function() {
                    setTimeout(function() {
                        var selection = window.getSelection();
                        var text = selection.toString().trim();
                        if (text.length > 0) {
                            showCustomMenu();
                        }
                    }, 100);
                });

                // 监听鼠标按键释放事件
                document.addEventListener('mouseup', function() {
                    setTimeout(function() {
                        var selection = window.getSelection();
                        var text = selection.toString().trim();
                        if (text.length > 0) {
                            showCustomMenu();
                        }
                    }, 100);
                });

                // 点击其他地方隐藏菜单
                document.addEventListener('mousedown', function(e) {
                    if (!e.target.closest('#custom_selection_menu')) {
                        hideCustomMenu();
                    }
                });

                // 滚动时隐藏菜单
                document.addEventListener('scroll', function() {
                    hideCustomMenu();
                }, true);

                // 启用文本选择
                var styleSheet = document.createElement('style');
                styleSheet.textContent = `
                    * {
                        -webkit-user-select: text !important;
                        user-select: text !important;
                    }
                    input, textarea {
                        -webkit-user-select: auto !important;
                        user-select: auto !important;
                    }
                    #custom_selection_menu, #custom_selection_menu * {
                        -webkit-user-select: none !important;
                        user-select: none !important;
                    }
                `;
                document.head.appendChild(styleSheet);

                // 禁用默认的长按菜单
                document.addEventListener('contextmenu', function(e) {
                    e.preventDefault();
                });
            })();
        """.trimIndent()

        webView.evaluateJavascript(js) { result ->
            Log.d(TAG, "注入自定义菜单代码完成: $result")
        }
    }

    private fun activateSelection(webView: WebView, event: MotionEvent) {
        if (isMenuShowing.get() || isMenuAnimating.get()) {
            hideTextSelectionMenu()
        }
        
        val script = """
            (function() {
                try {
                    var x = ${event.x};
                    var y = ${event.y};
                    
                    var elem = document.elementFromPoint(x, y);
                    if (!elem) return null;
                    
                    var isText = elem.nodeType === 3 || 
                                (elem.nodeType === 1 && 
                                 (elem.tagName === 'P' || 
                                  elem.tagName === 'SPAN' || 
                                  elem.tagName === 'DIV' || 
                                  /H[1-6]/.test(elem.tagName)));
                    
                    if (isText) {
                        var range = document.caretRangeFromPoint(x, y);
                        if (!range) return null;
                        
                        var selection = window.getSelection();
                        selection.removeAllRanges();
                        selection.addRange(range);
                        
                        var rect = range.getBoundingClientRect();
                        var selectedText = selection.toString()
                            .replace(/[\n\r]/g, ' ')  // 替换换行为空格
                            .replace(/[\\"]/g, '\\\\"');  // 转义引号
                        
                        return JSON.stringify({
                            text: selectedText,
                            left: {
                                x: Math.round(rect.left + window.scrollX),
                                y: Math.round(rect.bottom + window.scrollY)
                            },
                            right: {
                                x: Math.round(rect.right + window.scrollX),
                                y: Math.round(rect.bottom + window.scrollY)
                            }
                        });
                    }
                    return null;
                } catch(e) {
                    console.error('Selection error:', e);
                    return null;
                }
            })();
        """.trimIndent()

        webView.evaluateJavascript(script) { result ->
            try {
                if (result != "null") {
                    // 使用修复的方法解析JSON
                    val json = handleTextSelectionResult(result)
                    
                    if (json != null) {
                        val selectedText = json.optString("text", "")
                    if (selectedText.isNotEmpty()) {
                        currentSelectedText = selectedText
                        
                        mainHandler.post {
                                try {
                            val leftPos = json.getJSONObject("left")
                            val rightPos = json.getJSONObject("right")
                            
                            textSelectionManager?.showSelectionHandles(
                                leftPos.getInt("x"),
                                leftPos.getInt("y"),
                                rightPos.getInt("x"),
                                rightPos.getInt("y")
                            )
                            
                            showTextSelectionMenuSafely(webView, 
                                event.rawX.toInt(), 
                                event.rawY.toInt()
                            )
                                } catch (e: Exception) {
                                    Log.e(TAG, "处理选择位置信息失败", e)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "处理文本选择结果失败", e)
            }
        }
    }
    
    /**
     * 添加一个重载的activateSelection方法，接受不同的参数以解决冲突
     */
    private fun activateSelection(webView: WebView, event: MotionEvent, dummy: Boolean) {
        activateSelection(webView, event)
    }

    private fun clearTextSelection() {
        textSelectionManager?.clearSelection()
        textSelectionManager = null
        currentSelectionState = null
        hideTextSelectionMenu()
    }

    private fun showTextSelectionMenuSafely(webView: WebView, x: Int, y: Int) {
        webView.evaluateJavascript(
            "(function() { return window.getSelection().toString(); })();"
        ) { result ->
            val selectedText = result.trim('"')
            if (selectedText.isNotEmpty()) {
                com.example.aifloatingball.manager.TextSelectionMenuController.showMenu(
                    context = this,
                    windowManager = windowManager,
                    webView = webView,
                    x = x,
                    y = y,
                    selectedText = selectedText,
                    onMenuShown = {
                        // 菜单显示后的回调
                        Log.d(TAG, "文本选择菜单已显示")
                    }
                )
            }
        }
    }

    private fun hideTextSelectionMenu() {
        com.example.aifloatingball.manager.TextSelectionMenuController.hideMenu()
    }

    private fun copySelectedText(webView: WebView) {
        webView.evaluateJavascript(
            "(function() { return window.getSelection().toString(); })();"
        ) { result ->
            if (result != "null") {
                val text = result.trim('"')
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("选中文本", text)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun shareSelectedText(webView: WebView) {
        webView.evaluateJavascript(
            "(function() { return window.getSelection().toString(); })();"
        ) { result ->
            if (result != "null") {
                val text = result.trim('"')
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                }
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(Intent.createChooser(intent, "分享文本").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }

    private fun hideTextSelectionMenuSync() {
        if (!isMenuShowing.get() && !isMenuAnimating.get()) {
            return
        }

        menuShowLock.lock()
        try {
            isMenuAnimating.set(true)
            textSelectionMenu?.let { popup ->
                try {
                    // 获取PopupWindow的内容视图
                    val contentView = popup.contentView
                    if (contentView != null && contentView.isAttachedToWindow) {
                        // 使用内容视图进行动画，而不是PopupWindow本身
                        contentView.animate()
                            .alpha(0f)
                            .scaleX(0.8f)
                            .scaleY(0.8f)
                            .setDuration(150)
                            .setInterpolator(AccelerateInterpolator())
                            .withEndAction {
                                dismissPopupSafely(popup)
                                TextSelectionMenuController.cleanupState()
                            }
                            .start()
                        
                        // 等待动画完成，设置超时防止死锁
                        if (!menuAnimationComplete.await(MENU_SHOW_TIMEOUT, TimeUnit.MILLISECONDS)) {
                            Log.w(TAG, "等待动画完成超时")
                            dismissPopupSafely(popup)
                            TextSelectionMenuController.cleanupState()
                        }
                    } else {
                        // 内容视图不存在或未附加到窗口，直接关闭弹出窗口
                        dismissPopupSafely(popup)
                        TextSelectionMenuController.cleanupState()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "隐藏文本选择菜单失败", e)
                    dismissPopupSafely(popup)
                    TextSelectionMenuController.cleanupState()
                }
            } ?: run {
                // 如果没有菜单，直接清理状态
                TextSelectionMenuController.cleanupState()
            }
        } finally {
            menuShowLock.unlock()
        }
        mainHandler.removeCallbacksAndMessages(null)
    }

    // 辅助方法：dp转像素
    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).roundToInt()
    }

    // 剪贴板操作
    private fun copyToClipboard(text: String) {
        val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = ClipData.newPlainText("selected text", text)
        clipboardManager.setPrimaryClip(clipData)
        Log.d(TAG, "已复制文本: ${text.take(20)}${if (text.length > 20) "..." else ""}")
    }
    
    private fun pasteToWebView(webView: WebView) {
        val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        if (clipboardManager.hasPrimaryClip()) {
            val clipData = clipboardManager.primaryClip
            val clipText = clipData?.getItemAt(0)?.text?.toString() ?: ""
            
            if (clipText.isNotEmpty()) {
                // 使用JavaScript插入文本
                val escapedText = clipText.replace("\\", "\\\\").replace("'", "\\'")
                    .replace("\n", "\\n").replace("\r", "\\r")
                
                webView.evaluateJavascript(
                    "javascript:(function() { document.execCommand('insertText', false, '$escapedText'); })();",
                    null
                )
            }
        } else {
            Toast.makeText(this, "剪贴板为空", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 设置搜索引擎切换按钮
     */
    private fun setupEngineToggleButtons() {
        Log.d(TAG, "初始化引擎切换按钮")
        
        // 第一个窗口的切换按钮
        firstEngineToggle?.setOnClickListener {
            val webView = firstWebView ?: return@setOnClickListener
            val engineContainer = firstEngineContainer ?: return@setOnClickListener
            val aiContainer = firstAIEngineContainer ?: return@setOnClickListener
            val aiScrollContainer = firstAIScrollContainer ?: return@setOnClickListener
            
            val data = EngineToggleData(
                webView = webView,
                engineContainer = engineContainer,
                aiContainer = aiContainer,
                aiScrollContainer = aiScrollContainer,
                position = "left",
                saveEngineKey = { key ->
                    leftEngineKey = key
                    settingsManager.setLeftWindowSearchEngine(key)
                }
            )
            
             handleEngineToggle(
                data = data,
                toggleButton = it as ImageView,
                isShowingNormal = engineContainer.visibility == View.VISIBLE
            )
        }
        
        // 第二个窗口的切换按钮
        secondEngineToggle?.setOnClickListener {
            val webView = secondWebView ?: return@setOnClickListener
            val engineContainer = secondEngineContainer ?: return@setOnClickListener
            val aiContainer = secondAIEngineContainer ?: return@setOnClickListener
            val aiScrollContainer = secondAIScrollContainer ?: return@setOnClickListener
            
            val data = EngineToggleData(
                webView = webView,
                engineContainer = engineContainer,
                aiContainer = aiContainer,
                aiScrollContainer = aiScrollContainer,
                position = "center",
                saveEngineKey = { key ->
                    centerEngineKey = key
                    settingsManager.setCenterWindowSearchEngine(key)
                }
            )
            
            handleEngineToggle(
                data = data,
                toggleButton = it as ImageView,
                isShowingNormal = engineContainer.visibility == View.VISIBLE
            )
        }
        
        // 第三个窗口的切换按钮
        thirdEngineToggle?.setOnClickListener {
            val webView = thirdWebView ?: return@setOnClickListener
            val engineContainer = thirdEngineContainer ?: return@setOnClickListener
            val aiContainer = thirdAIEngineContainer ?: return@setOnClickListener
            val aiScrollContainer = thirdAIScrollContainer ?: return@setOnClickListener
            
            val data = EngineToggleData(
                webView = webView,
                engineContainer = engineContainer,
                aiContainer = aiContainer,
                aiScrollContainer = aiScrollContainer,
                position = "right",
                saveEngineKey = { key ->
                    rightEngineKey = key
                    settingsManager.setRightWindowSearchEngine(key)
                }
            )
            
            handleEngineToggle(
                data = data,
                toggleButton = it as ImageView,
                isShowingNormal = engineContainer.visibility == View.VISIBLE
            )
        }
    }

    /**
     * 设置控件事件
     */
    private fun setupControls() {
        // 设置窗口切换按钮
        toggleLayoutButton?.setOnClickListener {
            isHorizontalLayout = !isHorizontalLayout
            updateLayoutOrientation()
        }

                // 移除搜索按钮，改为仅通过搜索输入框的回车键执行搜索

        // 设置关闭按钮
        closeButton?.setOnClickListener {
            stopSelf()
        }

        // 设置搜索输入框
        searchInput?.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performDualSearch()
                true
            } else {
                false
            }
        }

        // 设置保存按钮
        saveButton?.setOnClickListener {
            saveSearchEngines()
        }

        // 设置切换按钮
        switchToNormalButton?.setOnClickListener {
            switchToNormal()
        }
        
        // 设置窗口数量切换按钮
        val windowCountButton = floatingView?.findViewById<ImageButton>(R.id.btn_window_count)
        windowCountButton?.setOnClickListener {
            try {
                // 循环切换窗口数量：1 -> 2 -> 3 -> 1
                val newCount = if (windowCount >= 3) 1 else windowCount + 1
                updateWindowVisibility(newCount)
                Toast.makeText(this, "已切换到${newCount}窗口模式", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "窗口数量切换为: $newCount")
            } catch (e: Exception) {
                Log.e(TAG, "切换窗口数量失败", e)
                Toast.makeText(this, "切换失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
        
        // 设置单窗口按钮
        singleWindowButton?.setOnClickListener {
            try {
                // 直接切换到单窗口模式
                updateWindowVisibility(1)
                Toast.makeText(this, "已切换到单窗口模式", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "切换到单窗口模式")
            } catch (e: Exception) {
                Log.e(TAG, "切换到单窗口失败", e)
                Toast.makeText(this, "切换失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
        
        // 初始化搜索引擎切换工具栏
        setupSearchEngineToolbars()
        
        // 单独设置搜索引擎切换按钮，确保不被覆盖
        refreshEngineToggleButtons()
        
        Log.d(TAG, "搜索引擎按钮设置完成")
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        try {
            // Dismiss any open popup
            hideTextSelectionMenuSync()
            
            windowManager.removeView(floatingView)
            
            // 清理IconLoader缓存
            if (::iconLoader.isInitialized) {
                iconLoader.clearCache()
            }
            
            // 停止前台服务
            stopForeground(true)
        } catch (e: Exception) {
            Log.e(TAG, "移除视图失败", e)
        }
        clearTextSelection()
    }

    private fun createEngineIcon(engineName: String): ImageView {
        val imageView = ImageView(this)
        // 增大图标尺寸从80dp到100dp
        val size = 25.dpToPx(this)
        val layoutParams = LinearLayout.LayoutParams(size, size)
        layoutParams.setMargins(8.dpToPx(this), 8.dpToPx(this), 8.dpToPx(this), 8.dpToPx(this))
        
        // 减小内边距使图标可以显示更大
        val padding = 8.dpToPx(this)
        imageView.setPadding(padding, padding, padding, padding)
        imageView.layoutParams = layoutParams
        
        // 设置高质量图片渲染
        imageView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        imageView.scaleType = ImageView.ScaleType.FIT_CENTER
        
        // 设置背景
        imageView.setBackgroundResource(R.drawable.icon_background)
        
        val engineIcon = when (engineName.lowercase()) {
            "google" -> R.drawable.ic_google
            "bing" -> R.drawable.ic_bing
            "baidu" -> R.drawable.ic_baidu
            "weibo" -> R.drawable.ic_weibo
            "douban" -> R.drawable.ic_douban
            "taobao" -> R.drawable.ic_taobao
            "jd" -> R.drawable.ic_jd
            "douyin" -> R.drawable.ic_douyin
            "xiaohongshu" -> R.drawable.ic_xiaohongshu
            else -> R.drawable.ic_search
        }
        imageView.setImageResource(engineIcon)
        
        return imageView
    }

    private fun performSearch(webView: WebView, query: String, searchUrl: String) {
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = searchUrl.replace("{query}", encodedQuery)
            webView.loadUrl(url)
        } catch (e: Exception) {
            Log.e(TAG, "执行搜索失败: $e")
            Toast.makeText(this, "搜索失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createSelectorEngineIcon(engineName: String): ImageView {
        val imageView = ImageView(this)
        // 减小图标尺寸从90dp到55dp
        val size = 25.dpToPx(this)
        // 使用LinearLayout的LayoutParams，因为它会被添加到LinearLayout
        val layoutParams = LinearLayout.LayoutParams(size, size)
        layoutParams.setMargins(3.dpToPx(this), 3.dpToPx(this), 3.dpToPx(this), 3.dpToPx(this))
        
        // 减小内边距使图标可以显示更大
        val padding = 4.dpToPx(this)
        imageView.setPadding(padding, padding, padding, padding)
        imageView.layoutParams = layoutParams
        
        // 设置高质量图片渲染
        imageView.scaleType = ImageView.ScaleType.FIT_CENTER
        // 启用硬件加速以提高渲染质量
        imageView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        
        // 设置背景
        imageView.setBackgroundResource(R.drawable.icon_background)
        
        // 使用本地高清图标资源
        val engineIcon = when (engineName.lowercase()) {
            "google" -> R.drawable.ic_google
            "bing" -> R.drawable.ic_bing
            "baidu" -> R.drawable.ic_baidu
            "sogou" -> R.drawable.ic_sogou
            "360" -> R.drawable.ic_360
            "quark" -> R.drawable.ic_search
            "toutiao" -> R.drawable.ic_search
            "zhihu" -> R.drawable.ic_zhihu
            "bilibili" -> R.drawable.ic_bilibili
            "weibo" -> R.drawable.ic_weibo
            "douban" -> R.drawable.ic_douban
            "taobao" -> R.drawable.ic_taobao
            "jd" -> R.drawable.ic_jd
            "douyin" -> R.drawable.ic_douyin
            "xiaohongshu" -> R.drawable.ic_xiaohongshu
            "qq" -> R.drawable.ic_qq
            "wechat" -> R.drawable.ic_qq
            else -> R.drawable.ic_search
        }
        imageView.setImageResource(engineIcon)
        
        return imageView
    }

    /**
     * 自定义HorizontalScrollView，用于处理搜索引擎列表的滚动
     */
    private inner class EngineScrollView(context: Context) : HorizontalScrollView(context) {
        private var startX = 0f
        private var startY = 0f
        private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

        override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = ev.x
                    startY = ev.y
                    parent.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = Math.abs(ev.x - startX)
                    val deltaY = Math.abs(ev.y - startY)
                    if (deltaX > touchSlop && deltaX > deltaY) {
                        parent.requestDisallowInterceptTouchEvent(true)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    parent.requestDisallowInterceptTouchEvent(false)
                }
            }
            return super.onInterceptTouchEvent(ev)
        }

        override fun onTouchEvent(ev: MotionEvent): Boolean {
            when (ev.action) {
                MotionEvent.ACTION_MOVE -> {
                    parent.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    parent.requestDisallowInterceptTouchEvent(false)
                }
            }
            return super.onTouchEvent(ev)
        }
    }

    private fun createSearchEngineSelector(searchBarLayout: LinearLayout, webView: WebView, isCenter: Boolean = false) {
        // 创建搜索引擎容器
        val engineContainer = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1A000000"))
            
            // 拦截所有横向滑动事件
            setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        // 阻止事件传递给ViewPager
                        parent.requestDisallowInterceptTouchEvent(true)
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        parent.requestDisallowInterceptTouchEvent(false)
                        true
                    }
                    else -> true
                }
            }
        }

        // 创建水平滚动视图
        val horizontalScrollView = HorizontalScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            isHorizontalScrollBarEnabled = true
            overScrollMode = View.OVER_SCROLL_NEVER
            
            // 禁用横向滚动
            setOnTouchListener { _, _ -> true }
        }

        // 创建引擎图标容器
        val engineLayout = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.HORIZONTAL
            setPadding(8.dpToPx(this@DualFloatingWebViewService),
                      4.dpToPx(this@DualFloatingWebViewService),
                      8.dpToPx(this@DualFloatingWebViewService),
                      4.dpToPx(this@DualFloatingWebViewService))
        }

        // 添加搜索引擎图标
        val engines = SearchEngine.DEFAULT_ENGINES
        engines.forEach { engine ->
            val engineIcon = createSelectorEngineIcon(engine.name).apply {
                // 设置图标的布局参数
                val iconParams = LinearLayout.LayoutParams(
                    40.dpToPx(this@DualFloatingWebViewService),
                    40.dpToPx(this@DualFloatingWebViewService)
                ).apply {
                    marginEnd = 8.dpToPx(this@DualFloatingWebViewService)
                }
                layoutParams = iconParams

                // 设置点击事件
                setOnClickListener {
                    // 更新选中状态
                    for (i in 0 until engineLayout.childCount) {
                        val view = engineLayout.getChildAt(i)
                        view.setBackgroundResource(0)
                    }
                    setBackgroundResource(R.drawable.icon_background_selected)
                
                // 保存选择的搜索引擎
                val windowPosition = if (isCenter) "center" else if (webView == firstWebView) "left" else "right"
                when (windowPosition) {
                        "left" -> settingsManager.setLeftWindowSearchEngine(engine.name.lowercase())
                        "center" -> settingsManager.setCenterWindowSearchEngine(engine.name.lowercase())
                        else -> settingsManager.setRightWindowSearchEngine(engine.name.lowercase())
                    }

                    // 执行搜索
                    val searchText = searchInput?.text?.toString() ?: ""
                if (searchText.isNotEmpty()) {
                        performSearch(webView, searchText, engine.url)
                    }
                }
            }
            engineLayout.addView(engineIcon)
            
            // 设置默认选中状态
            val windowPosition = if (isCenter) "center" else if (webView == firstWebView) "left" else "right"
            val defaultEngine = when (windowPosition) {
                "left" -> settingsManager.getLeftWindowSearchEngine()
                "center" -> settingsManager.getCenterWindowSearchEngine()
                else -> settingsManager.getRightWindowSearchEngine()
            }
            if (engine.name.lowercase() == defaultEngine) {
                engineIcon.setBackgroundResource(R.drawable.icon_background_selected)
            }
        }

        // 组装视图
        horizontalScrollView.addView(engineLayout)
        engineContainer.addView(horizontalScrollView)
        searchBarLayout.addView(engineContainer)

        // 滚动到选中的引擎位置
        horizontalScrollView.post {
            val selectedIndex = engines.indexOfFirst { 
                val windowPosition = if (isCenter) "center" else if (webView == firstWebView) "left" else "right"
                val defaultEngine = when (windowPosition) {
                    "left" -> settingsManager.getLeftWindowSearchEngine()
                    "center" -> settingsManager.getCenterWindowSearchEngine()
                    else -> settingsManager.getRightWindowSearchEngine()
                }
                it.name.lowercase() == defaultEngine
            }
            
            if (selectedIndex > 2) {
                val childView = engineLayout.getChildAt(selectedIndex)
                horizontalScrollView.smoothScrollTo(childView.left - 30.dpToPx(this), 0)
            }
        }
    }

    /**
     * 初始化搜索引擎切换工具栏
     */
    private fun setupSearchEngineToolbars() {
        try {
            Log.d(TAG, "开始初始化搜索引擎工具栏")
            
            // 检查对应的视图是否存在
            if (firstWebView == null || secondWebView == null || thirdWebView == null) {
                Log.e(TAG, "WebView未正确初始化: $firstWebView, $secondWebView, $thirdWebView")
                return
            }
            
            if (firstEngineContainer == null || secondEngineContainer == null || thirdEngineContainer == null) {
                Log.e(TAG, "引擎容器未正确初始化: $firstEngineContainer, $secondEngineContainer, $thirdEngineContainer")
                return
            }
            
            // 初始化第一个WebView的工具栏
            setupWebViewToolbar(
                firstWebView,
                firstEngineContainer,
                firstAIEngineContainer,
                firstEngineToggle,
                leftEngineKey,
                true
            )
            
            // 初始化第二个WebView的工具栏
            setupWebViewToolbar(
                secondWebView,
                secondEngineContainer,
                secondAIEngineContainer,
                secondEngineToggle,
                centerEngineKey,
                false
            )
            
            // 初始化第三个WebView的工具栏
            setupWebViewToolbar(
                thirdWebView,
                thirdEngineContainer,
                thirdAIEngineContainer,
                thirdEngineToggle,
                rightEngineKey,
                false
            )
            
            // 确保工具栏的可见性与窗口数量一致
            updateToolbarVisibility()
            
            Log.d(TAG, "搜索引擎工具栏初始化完成")
        } catch (e: Exception) {
            Log.e(TAG, "初始化搜索引擎工具栏失败", e)
        }
    }

    /**
     * 设置单个WebView的搜索引擎工具栏
     */
    private fun setupWebViewToolbar(
        webView: WebView?,
        normalEngineContainer: LinearLayout?,
        aiEngineContainer: LinearLayout?,
        toggleButton: ImageButton?,
        defaultEngine: String,
        isLeftWindow: Boolean
    ) {
        webView ?: return
        normalEngineContainer ?: return
        aiEngineContainer ?: return
        toggleButton ?: return
        
        // 清空现有图标 - 确保彻底清空所有子视图
        normalEngineContainer.removeAllViews()
        aiEngineContainer.removeAllViews()
        
        // 记录初始状态
        val isAIEngine = defaultEngine.startsWith("ai_")
        Log.d(TAG, "初始化工具栏 - 默认引擎: $defaultEngine, 是否为AI引擎: $isAIEngine")
        
        // 添加常规搜索引擎图标
        val normalEngines = listOf(
            SearchEngine("百度", "https://www.baidu.com/s?wd={query}", R.drawable.ic_baidu, "百度搜索"),
            SearchEngine("谷歌", "https://www.google.com/search?q={query}", R.drawable.ic_google, "谷歌搜索"),
            SearchEngine("必应", "https://www.bing.com/search?q={query}", R.drawable.ic_bing, "必应搜索"),
            SearchEngine("搜狗", "https://www.sogou.com/web?query={query}", R.drawable.ic_sogou, "搜狗搜索"),
            SearchEngine("360", "https://www.so.com/s?q={query}", R.drawable.ic_360, "360搜索"),
            SearchEngine("知乎", "https://www.zhihu.com/search?q={query}", R.drawable.ic_zhihu, "知乎搜索"),
            SearchEngine("微博", "https://s.weibo.com/weibo?q={query}", R.drawable.ic_weibo, "微博搜索")
        )

        // 添加AI搜索引擎图标
        val aiEngines = listOf(
            SearchEngine("ChatGPT", "https://chat.openai.com/", R.drawable.ic_search, "ChatGPT"),
            SearchEngine("Claude", "https://claude.ai/", R.drawable.ic_baidu, "Claude AI助手"),
            SearchEngine("文心一言", "https://yiyan.baidu.com/", R.drawable.ic_baidu, "文心一言"),
            SearchEngine("通义千问", "https://qianwen.aliyun.com/", R.drawable.ic_google, "阿里通义千问"),
            SearchEngine("讯飞星火", "https://xinghuo.xfyun.cn/", R.drawable.ic_sogou, "讯飞星火")
        )
        
        // 添加常规搜索引擎图标
        addSearchEngineIcons(normalEngineContainer, normalEngines, webView, defaultEngine, false)
        
        // 添加AI搜索引擎图标
        addSearchEngineIcons(aiEngineContainer, aiEngines, webView, defaultEngine, true)
        
        // 获取对应的AI滚动容器
        val aiScrollContainer = when (webView) {
            firstWebView -> firstAIScrollContainer
            secondWebView -> secondAIScrollContainer
            thirdWebView -> thirdAIScrollContainer
            else -> null
        }
        
                // 初始化搜索引擎容器的可见性        normalEngineContainer.visibility = View.VISIBLE        aiScrollContainer?.visibility = View.GONE        toggleButton.setImageResource(R.drawable.ic_ai_search)        // 设置切换按钮的点击事件        toggleButton.setOnClickListener {            // 切换搜索引擎容器的可见性            val isShowingNormal = normalEngineContainer.visibility == View.VISIBLE                        if (isShowingNormal) {                // 切换到 AI 搜索引擎                normalEngineContainer.visibility = View.GONE                aiScrollContainer?.visibility = View.VISIBLE                toggleButton.setImageResource(R.drawable.ic_search)                                // 保存当前状态                settingsManager.setIsAIMode(true)                                // 更新搜索框提示文本                searchInput?.hint = "输入要问AI的内容..."            } else {                // 切换到普通搜索引擎                normalEngineContainer.visibility = View.VISIBLE                aiScrollContainer?.visibility = View.GONE                toggleButton.setImageResource(R.drawable.ic_ai_search)                                // 保存当前状态                settingsManager.setIsAIMode(false)                                // 更新搜索框提示文本                searchInput?.hint = "输入搜索关键词..."            }        }        // 禁用其他可能干扰切换的点击事件        normalEngineContainer.setOnClickListener(null)        aiScrollContainer?.setOnClickListener(null)                
        // 设置切换按钮点击事件
        toggleButton.setOnClickListener {
            val isCurrentlyAI = aiScrollContainer?.visibility == View.VISIBLE
            if (isCurrentlyAI) {
                // 切换到普通搜索
                aiScrollContainer?.visibility = View.GONE
                normalEngineContainer.visibility = View.VISIBLE
                toggleButton.setImageResource(R.drawable.ic_ai_search)
            } else {
                // 切换到AI搜索
                aiScrollContainer?.visibility = View.VISIBLE
                normalEngineContainer.visibility = View.GONE
                toggleButton.setImageResource(R.drawable.ic_search)
            }
        }
    }

    /**
     * 添加搜索引擎图标到容器
     */
    private fun addSearchEngineIcons(container: LinearLayout, engines: List<SearchEngine>, webView: WebView, defaultEngine: String, isAI: Boolean) {
        // 创建水平滚动视图
        val scrollView = HorizontalScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            isHorizontalScrollBarEnabled = true
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        
        // 创建内部图标容器
        val iconsContainer = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.HORIZONTAL
            setPadding(4.dpToPx(this@DualFloatingWebViewService), 
                      2.dpToPx(this@DualFloatingWebViewService), 
                      4.dpToPx(this@DualFloatingWebViewService), 
                      2.dpToPx(this@DualFloatingWebViewService))
        }
        
        // 添加引擎图标
        engines.forEach { engine ->
            val engineKey = if (isAI) "ai_${engine.name.lowercase()}" else engine.name.lowercase()
            val iconView = createEngineIconButton(engine, engineKey)
            
            // 加载图标
            loadEngineIcon(iconView, engine)
            
            // 设置点击事件
            iconView.setOnClickListener {
                // 执行搜索
                val query = searchInput?.text?.toString() ?: ""
                if (query.isNotEmpty()) {
                    if (isAI) {
                        performAISearch(webView, query, engine.url, engine.name)
                    } else {
                        performSearch(webView, query, engine.url)
                    }
                } else {
                    // 如果搜索框为空，直接加载引擎首页
                    val baseUrl = engine.url.split("?")[0]
                    webView.loadUrl(baseUrl)
                }
                
                // 更新选中状态
                updateEngineSelection(container, null, engineKey)
                
                // 切换到按钮面板
                switchToButtonPanel(webView, container)
                
                // 更新对应窗口的默认引擎
                when (webView) {
                    firstWebView -> {
                        leftEngineKey = engineKey
                        settingsManager.setLeftWindowSearchEngine(engineKey)
                    }
                    secondWebView -> {
                        centerEngineKey = engineKey
                        settingsManager.setCenterWindowSearchEngine(engineKey)
                    }
                    thirdWebView -> {
                        rightEngineKey = engineKey
                        settingsManager.setRightWindowSearchEngine(engineKey)
                    }
                }
                
                // 添加点击反馈
                iconView.animate()
                    .scaleX(1.2f)
                    .scaleY(1.2f)
                    .setDuration(100)
                    .withEndAction {
                        iconView.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(100)
                            .start()
                    }
                    .start()
                
                // 显示提示
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(
                        this@DualFloatingWebViewService,
                        "已切换到${engine.name}${if (isAI) "AI" else ""}搜索",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            
            // 设置标签
            iconView.tag = engineKey
            
            // 添加到容器
            iconsContainer.addView(iconView)
        }
        
        // 将图标容器添加到滚动视图
        scrollView.addView(iconsContainer)
        
        // 将滚动视图添加到引擎容器
        container.addView(scrollView)
        
        // 更新选中状态
        updateEngineSelection(container, null, defaultEngine)
    }

    /**
     * 创建搜索引擎图标按钮
     */
    private fun createEngineIconButton(engine: SearchEngine, engineKey: String): ImageView {
        return ImageView(this).apply {
            val size = 40.dpToPx(this@DualFloatingWebViewService)
            val layoutParams = LinearLayout.LayoutParams(size, size).apply {
                setMargins(
                    4.dpToPx(this@DualFloatingWebViewService),
                    2.dpToPx(this@DualFloatingWebViewService),
                    4.dpToPx(this@DualFloatingWebViewService),
                    2.dpToPx(this@DualFloatingWebViewService)
                )
            }
            this.layoutParams = layoutParams
            
            // 设置默认图标
            setImageResource(R.drawable.ic_search)
            
            // 设置背景
            setBackgroundResource(R.drawable.icon_background)
            
            // 设置内边距
            val padding = 6.dpToPx(this@DualFloatingWebViewService)
            setPadding(padding, padding, padding, padding)
            
            // 设置缩放类型
            scaleType = ImageView.ScaleType.FIT_CENTER
            
            // 添加描述
            contentDescription = engine.description
            
            // 启用硬件加速
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            
            // 设置可见性为可见
            visibility = View.VISIBLE
        }
    }

    /**
     * 更新引擎选中状态
     */
    private fun updateEngineSelection(
        normalContainer: ViewGroup,
        aiContainer: ViewGroup?,
        selectedEngineKey: String
    ) {
        // 处理常规搜索引擎容器
        updateContainerSelection(normalContainer, selectedEngineKey)
        
        // 处理AI搜索引擎容器
        aiContainer?.let {
            updateContainerSelection(it, selectedEngineKey)
        }
    }

    /**
     * 更新容器内图标的选中状态
     */
    private fun updateContainerSelection(container: ViewGroup, selectedEngineKey: String) {
        // 遍历容器中的所有视图
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            if (child is HorizontalScrollView && child.childCount > 0) {
                val iconContainer = child.getChildAt(0) as? ViewGroup ?: continue
                
                // 遍历图标容器中的所有图标
                for (j in 0 until iconContainer.childCount) {
                    val iconView = iconContainer.getChildAt(j)
                    if (iconView is ImageView) {
                        val engineKey = iconView.tag as? String
                        
                        // 更新选中状态
                        if (engineKey == selectedEngineKey) {
                            iconView.setBackgroundResource(R.drawable.icon_background_selected)
                            // 滚动到可见位置
                            child.post {
                                child.smoothScrollTo(
                                    (iconView.left + iconView.right - child.width) / 2,
                                    0
                                )
                            }
                        } else {
                            iconView.setBackgroundResource(R.drawable.icon_background)
                        }
                    }
                }
            }
        }
    }

    /**
     * 更新工具栏可见性
     */
    private fun updateToolbarVisibility() {
        when (windowCount) {
            1 -> {
                firstEngineContainer?.visibility = View.VISIBLE
                firstEngineToggle?.visibility = View.VISIBLE
                secondEngineContainer?.visibility = View.GONE
                secondEngineToggle?.visibility = View.GONE
                thirdEngineContainer?.visibility = View.GONE
                thirdEngineToggle?.visibility = View.GONE
            }
            2 -> {
                firstEngineContainer?.visibility = View.VISIBLE
                firstEngineToggle?.visibility = View.VISIBLE
                secondEngineContainer?.visibility = View.VISIBLE
                secondEngineToggle?.visibility = View.VISIBLE
                thirdEngineContainer?.visibility = View.GONE
                thirdEngineToggle?.visibility = View.GONE
            }
            else -> {
                firstEngineContainer?.visibility = View.VISIBLE
                firstEngineToggle?.visibility = View.VISIBLE
                secondEngineContainer?.visibility = View.VISIBLE
                secondEngineToggle?.visibility = View.VISIBLE
                thirdEngineContainer?.visibility = View.VISIBLE
                thirdEngineToggle?.visibility = View.VISIBLE
            }
        }
    }

    private fun updateLayoutOrientation() {
        container?.orientation = if (isHorizontalLayout) {
            LinearLayout.HORIZONTAL
        } else {
            LinearLayout.VERTICAL
        }
        
        // 更新布局参数
        updateLayoutParams()
        
        // 保存布局方向
        sharedPrefs.edit().putBoolean(KEY_IS_HORIZONTAL, isHorizontalLayout).apply()
    }

    private fun getSearchUrl(engineKey: String, query: String): String {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        return when(engineKey.lowercase()) {
            "baidu" -> "https://www.baidu.com/s?wd=$encodedQuery"
            "google" -> "https://www.google.com/search?q=$encodedQuery"
            "bing" -> "https://www.bing.com/search?q=$encodedQuery"
            "sogou" -> "https://www.sogou.com/web?query=$encodedQuery"
            "360" -> "https://www.so.com/s?q=$encodedQuery"
            "zhihu" -> "https://www.zhihu.com/search?q=$encodedQuery"
            "bilibili" -> "https://search.bilibili.com/all?keyword=$encodedQuery"
            "weibo" -> "https://s.weibo.com/weibo?q=$encodedQuery"
            "douban" -> "https://www.douban.com/search?q=$encodedQuery"
            "taobao" -> "https://s.taobao.com/search?q=$encodedQuery"
            "jd" -> "https://search.jd.com/Search?keyword=$encodedQuery"
            "douyin" -> "https://www.douyin.com/search/$encodedQuery"
            "xiaohongshu" -> "https://www.xiaohongshu.com/search_result?keyword=$encodedQuery"
            else -> "https://www.baidu.com/s?wd=$encodedQuery"
        }
    }

    private fun performDualSearch() {
        val query = searchInput?.text?.toString() ?: return
        if (query.isEmpty()) return

        // 执行搜索
        when (windowCount) {
            1 -> {
                firstWebView?.let { performSearch(it, query, getSearchUrl(leftEngineKey, query)) }
            }
            2 -> {
                firstWebView?.let { performSearch(it, query, getSearchUrl(leftEngineKey, query)) }
                secondWebView?.let { performSearch(it, query, getSearchUrl(centerEngineKey, query)) }
            }
            else -> {
                firstWebView?.let { performSearch(it, query, getSearchUrl(leftEngineKey, query)) }
                secondWebView?.let { performSearch(it, query, getSearchUrl(centerEngineKey, query)) }
                thirdWebView?.let { performSearch(it, query, getSearchUrl(rightEngineKey, query)) }
            }
        }

        // 隐藏输入法
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        searchInput?.let { imm.hideSoftInputFromWindow(it.windowToken, 0) }
    }

    private fun updateEngineIcons() {
        // 更新第一个窗口的图标
        firstEngineContainer?.let { updateEngineIconStates(it, leftEngineKey, false) }
        
        // 更新第二个窗口的图标
        secondEngineContainer?.let { updateEngineIconStates(it, centerEngineKey, false) }
        
        // 更新第三个窗口的图标
        thirdEngineContainer?.let { updateEngineIconStates(it, rightEngineKey, false) }
    }

    private fun updateEngineIconStates(container: LinearLayout, selectedEngine: String, isAI: Boolean) {
        for (i in 0 until container.childCount) {
            val view = container.getChildAt(i)
            if (view is ImageView) {
                val engineName = view.tag as? String
                if (engineName == selectedEngine) {
                    view.setBackgroundResource(R.drawable.icon_background_selected)
                } else {
                    view.setBackgroundResource(R.drawable.icon_background)
                }
            }
        }
    }

    private fun enableWebViewTextSelection(webView: WebView) {
        webView.settings.javaScriptEnabled = true
        
        // 注入JavaScript以启用文本选择
        val script = """
            (function() {
                document.documentElement.style.webkitUserSelect = 'text';
                document.documentElement.style.userSelect = 'text';
                
                document.addEventListener('contextmenu', function(e) {
                    e.preventDefault();
                });
                
                document.addEventListener('selectionchange', function() {
                    var selection = window.getSelection();
                    if (selection.toString().length > 0) {
                        window.textSelectionCallback.onSelectionChanged(selection.toString());
                    }
                });
            })();
        """.trimIndent()
        
        // 添加JavaScript接口
        webView.addJavascriptInterface(TextSelectionJavaScriptInterface(webView), "textSelectionCallback")
        
        webView.evaluateJavascript(script, null)
    }

    private fun enableTextSelectionMode(webView: WebView) {
        val script = """
            (function() {
                // 创建并应用样式，确保文本可选
                var styleEl = document.createElement('style');
                styleEl.textContent = `
                    *:not(input):not(textarea) {
                        -webkit-user-select: text !important;
                        user-select: text !important;
                    }
                    ::selection {
                        background: rgba(33, 150, 243, 0.4) !important;
                    }
                    input, textarea {
                        -webkit-user-select: auto !important;
                        user-select: auto !important;
                    }
                `;
                document.head.appendChild(styleEl);
                
                // 针对非输入框元素禁用默认的长按菜单
                document.addEventListener('contextmenu', function(e) {
                    if (e.target.tagName !== 'INPUT' && e.target.tagName !== 'TEXTAREA') {
                        e.preventDefault();
                        return false;
                    }
                }, true);
                
                // 针对特定元素优化选择行为
                var elements = document.querySelectorAll('p, div, span, h1, h2, h3, h4, h5, h6, article, section');
                for (var i = 0; i < elements.length; i++) {
                    if (elements[i].tagName !== 'INPUT' && elements[i].tagName !== 'TEXTAREA') {
                        elements[i].style.webkitUserSelect = 'text';
                        elements[i].style.userSelect = 'text';
                    }
                }
                
                console.log('文本选择模式已启用');
            })();
        """.trimIndent()
        
        webView.evaluateJavascript(script, null)
    }

    // 文本选择JavaScript接口
    private inner class TextSelectionJavaScriptInterface(private val webView: WebView) {
        @JavascriptInterface
        fun onSelectionChanged(
            selectedText: String,
            leftX: Float = 0f,
            leftY: Float = 0f,
            rightX: Float = 0f,
            rightY: Float = 0f
        ) {
            mainHandler.post {
                currentSelectedText = selectedText
                if (selectedText.isNotEmpty()) {
                    // 转换为相对于WebView的坐标
                    textSelectionManager?.showSelectionHandles(
                        leftX.toInt(),
                        leftY.toInt(),
                        rightX.toInt(),
                        rightY.toInt()
                    )
                }
            }
        }
    }

    /**
     * 处理长按事件
     */
    private fun handleLongPress(webView: WebView, event: MotionEvent) {
        webView.evaluateJavascript("""
            (function() {
                try {
                    var x = ${event.x};
                    var y = ${event.y};
                    
                    // 获取精确的文本节点和位置
                    function getPreciseTextNode(x, y) {
                        var range = document.caretRangeFromPoint(x, y);
                        if (!range) return null;
                        
                        var node = range.startContainer;
                        var offset = range.startOffset;
                        
                        // 确保我们得到的是文本节点
                        if (node.nodeType !== Node.TEXT_NODE) {
                            // 如果不是文本节点，查找最近的文本节点
                            var walker = document.createTreeWalker(
                                document.body,
                                NodeFilter.SHOW_TEXT,
                                null,
                                false
                            );
                            
                            var closestNode = null;
                            var minDistance = Infinity;
                            var currentNode;
                            
                            while (currentNode = walker.nextNode()) {
                                var rect = currentNode.parentElement.getBoundingClientRect();
                                var distance = Math.abs(rect.left - x) + Math.abs(rect.top - y);
                                if (distance < minDistance) {
                                    closestNode = currentNode;
                                    minDistance = distance;
                                }
                            }
                            
                            if (closestNode) {
                                node = closestNode;
                                // 根据点击位置确定偏移量
                                var nodeRect = node.parentElement.getBoundingClientRect();
                                offset = Math.floor((x - nodeRect.left) / 
                                    (nodeRect.width / node.length));
                            }
                        }
                        
                        return {
                            node: node,
                            offset: offset
                        };
                    }
                    
                    // 扩展选择范围到单词边界
                    function expandToWordBoundary(node, offset) {
                        var text = node.textContent;
                        var start = offset;
                        var end = offset;
                        
                        // 向前查找单词边界
                        while (start > 0 && /\\w/.test(text[start - 1])) {
                            start--;
                        }
                        
                        // 向后查找单词边界
                        while (end < text.length && /\\w/.test(text[end])) {
                            end++;
                        }
                        
                        return { start, end };
                    }
                    
                    // 获取选择范围的详细信息
                    function getSelectionDetails(range) {
                        var rects = range.getClientRects();
                        if (rects.length === 0) return null;
                        
                        var firstRect = rects[0];
                        var lastRect = rects[rects.length - 1];
                        var isRTL = getComputedStyle(range.startContainer.parentElement).direction === 'rtl';
                        
                        // 计算基线位置（用于垂直对齐）
                        var computedStyle = window.getComputedStyle(range.startContainer.parentElement);
                        var fontSize = parseFloat(computedStyle.fontSize);
                        var lineHeight = parseFloat(computedStyle.lineHeight) || fontSize * 1.2;
                        var baseline = firstRect.bottom - (lineHeight - fontSize) * 0.5;
                        
                        return {
                            text: range.toString(),
                            start: {
                                x: isRTL ? firstRect.right : firstRect.left,
                                y: baseline,
                                top: firstRect.top,
                                bottom: firstRect.bottom
                            },
                            end: {
                                x: isRTL ? lastRect.left : lastRect.right,
                                y: baseline,
                                top: lastRect.top,
                                bottom: lastRect.bottom
                            },
                            isRTL: isRTL,
                            lineHeight: lineHeight
                        };
                    }
                    
                    var element = document.elementFromPoint(x, y);
                    if (element && (element.tagName === 'INPUT' || element.tagName === 'TEXTAREA')) {
                        return JSON.stringify({
                            isInput: true,
                            value: element.value || '',
                            selectionStart: element.selectionStart,
                            selectionEnd: element.selectionEnd
                        });
                    }
                    
                    // 获取精确的点击位置
                    var position = getPreciseTextNode(x, y);
                    if (!position) {
                        return JSON.stringify({
                            isInput: false,
                            error: 'No text node found'
                        });
                    }
                    
                    // 创建选择范围
                    var selection = window.getSelection();
                    selection.removeAllRanges();
                    
                    var range = document.createRange();
                    range.setStart(position.node, position.offset);
                    range.setEnd(position.node, position.offset);
                    
                    // 扩展到单词边界
                    var boundary = expandToWordBoundary(position.node, position.offset);
                    range.setStart(position.node, boundary.start);
                    range.setEnd(position.node, boundary.end);
                    
                    selection.addRange(range);
                    
                    // 获取选择范围的详细信息
                    var details = getSelectionDetails(range);
                    if (!details) {
                        return JSON.stringify({
                            isInput: false,
                            error: 'Cannot get selection details'
                        });
                    }
                    
                    return JSON.stringify({
                        isInput: false,
                        ...details
                    });
                    
                } catch(e) {
                    console.error('Error in handleLongPress:', e);
                    return JSON.stringify({
                        isInput: false,
                        error: e.message
                    });
                }
            })();
        """.trimIndent()) { result ->
            try {
                val jsonString = result?.let {
                    if (it == "null" || it.isEmpty()) {
                        "{\"isInput\":false,\"error\":\"No result\"}"
                    } else {
                        it.trim('"').replace("\\\"", "\"")
                    }
                } ?: "{\"isInput\":false,\"error\":\"Null result\"}"
                
                Log.d(TAG, "JavaScript返回结果: $jsonString")
                
                val json = JSONObject(jsonString)
                val isInput = json.optBoolean("isInput", false)
                
                if (isInput) {
                    handleInputFieldLongPress(webView, event)
                } else {
                    handleTextNodeLongPress(webView, event, json)
                }
            } catch (e: Exception) {
                Log.e(TAG, "处理长按事件失败: ${e.message}", e)
                handleFallbackTextSelection(webView, event)
            }
        }
    }

    private fun handleTextNodeLongPress(webView: WebView, event: MotionEvent, json: JSONObject) {
        mainHandler.post {
            try {
                clearTextSelection()
                
                val start = json.optJSONObject("start")
                val end = json.optJSONObject("end")
                val isRTL = json.optBoolean("isRTL", false)
                val text = json.optString("text", "")
                
                if (start != null && end != null && text.isNotEmpty()) {
                    // 更新选择状态
                    currentSelectionState = SelectionHandleState(
                        startHandle = Point(
                            start.getInt("x"),
                            start.getInt("y")
                        ),
                        endHandle = Point(
                            end.getInt("x"),
                            end.getInt("y")
                        ),
                        isRTL = isRTL
                    )
                    
                    // 创建选择管理器
                    textSelectionManager = TextSelectionManager(
                        context = this,
                        webView = webView,
                        windowManager = windowManager,
                        onSelectionChanged = { selectedText ->
                            if (selectedText.isNotEmpty()) {
                                showTextSelectionMenuSafely(
                                    webView,
                                    (start.getInt("x") + end.getInt("x")) / 2,
                                    min(start.getInt("bottom"), end.getInt("bottom"))
                                )
                            }
                        },
                        onHandleMoved = { managerHandleType, x, y ->
                            // 将 manager 包中的 HandleType 转换为 model 包中的 HandleType
                            val modelHandleType = when (managerHandleType) {
                                com.example.aifloatingball.manager.HandleType.START -> HandleType.START
                                com.example.aifloatingball.manager.HandleType.END -> HandleType.END
                                else -> HandleType.NONE
                            }
                            updateSelectionForHandleMove(webView, modelHandleType, x, y)
                        }
                    )
                    
                    // 显示选择柄
                }
            } catch (e: Exception) {
                Log.e(TAG, "处理文本节点长按失败", e)
            }
        }
    }

    private fun updateSelectionForHandleMove(webView: WebView, handleType: HandleType, x: Int, y: Int) {
        val js = when (handleType) {
            HandleType.START -> """
                (function() {
                    const range = window.__selection_range;
                    if (!range) return;
                    
                    const point = document.caretRangeFromPoint($x, $y);
                    if (point) {
                        range.setStart(point.startContainer, point.startOffset);
                        window.__selection_range = range;
                        
                        const selection = window.getSelection();
                        selection.removeAllRanges();
                        selection.addRange(range);
                        
                        return {
                            text: selection.toString(),
                            start: getSelectionCoordinates(range, true),
                            end: getSelectionCoordinates(range, false)
                        };
                    }
                })();
            """
            HandleType.END -> """
                (function() {
                    const range = window.__selection_range;
                    if (!range) return;
                    
                    const point = document.caretRangeFromPoint($x, $y);
                    if (point) {
                        range.setEnd(point.startContainer, point.startOffset);
                        window.__selection_range = range;
                        
                        const selection = window.getSelection();
                        selection.removeAllRanges();
                        selection.addRange(range);
                        
                        return {
                            text: selection.toString(),
                            start: getSelectionCoordinates(range, true),
                            end: getSelectionCoordinates(range, false)
                        };
                    }
                })();
            """
            HandleType.NONE -> """
                (function() {
                    return null;
                })();
            """
        }
        
        webView.evaluateJavascript(js) { result ->
            try {
                if (result != "null") {
                    val json = JSONObject(result)
                    val text = json.optString("text", "")
                    val start = json.optJSONObject("start")
                    val end = json.optJSONObject("end")
                    
                    if (start != null && end != null) {
                        mainHandler.post {
                            textSelectionManager?.updateHandlePositions(
                                leftX = start.getInt("x"),
                                leftY = start.getInt("y"),
                                rightX = end.getInt("x"),
                                rightY = end.getInt("y")
                            )
                            
                            if (text.isNotEmpty()) {
                                showTextSelectionMenuSafely(webView, x, y)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "更新选择位置失败", e)
            }
        }
    }

    private fun handleInputFieldLongPress(webView: WebView, event: MotionEvent) {
        mainHandler.post {
            try {
                toggleWindowFocusableFlag(true)
                webView.evaluateJavascript("""
                    (function() {
                        try {
                            var element = document.elementFromPoint(${event.x}, ${event.y});
                            if (element) {
                                element.focus();
                                element.setSelectionRange(0, element.value.length);
                                return true;
                            }
                            return false;
                        } catch(e) {
                            console.error('Error focusing input:', e);
                            return false;
                        }
                    })();
                """.trimIndent()) { focusResult ->
                    Log.d(TAG, "输入框焦点设置结果: $focusResult")
                }
            } catch (e: Exception) {
                Log.e(TAG, "设置输入框焦点失败", e)
            }
        }
    }
    
    private fun handleFallbackTextSelection(webView: WebView, event: MotionEvent) {
        mainHandler.post {
            try {
                clearTextSelection()
                textSelectionManager = TextSelectionManager(
                    context = this,
                    webView = webView,
                    windowManager = windowManager,
                    onSelectionChanged = { selectedText ->
                        if (selectedText.isNotEmpty()) {
                            showTextSelectionMenuSafely(webView, event.rawX.toInt(), event.rawY.toInt())
                        }
                    },
                    onHandleMoved = { managerHandleType, x, y ->
                        // 将 manager 包中的 HandleType 转换为 model 包中的 HandleType
                        val modelHandleType = when (managerHandleType) {
                            com.example.aifloatingball.manager.HandleType.START -> HandleType.START
                            com.example.aifloatingball.manager.HandleType.END -> HandleType.END
                            else -> HandleType.NONE
                        }
                        updateSelectionForHandleMove(webView, modelHandleType, x, y)
                    }
                )
                textSelectionManager?.startSelection(event.x.toInt(), event.y.toInt())
                provideHapticFeedback()
            } catch (e: Exception) {
                Log.e(TAG, "回退到默认文本选择处理失败", e)
            }
        }
    }

    private fun provideHapticFeedback() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(50)
        }
    }

    // 添加选择柄状态追踪
    private data class SelectionHandleState(
        val startHandle: Point,
        val endHandle: Point,
        val isRTL: Boolean
    )

    private var currentSelectionState: SelectionHandleState? = null

    private fun dismissTextSelectionMenu() {
        textSelectionMenu?.let { popup ->
            try {
                // 直接使用 dismiss 方法关闭弹出窗口
                popup.dismiss()
                textSelectionMenu = null
            } catch (e: Exception) {
                Log.e(TAG, "关闭文本选择菜单失败", e)
                // 确保清理状态
                textSelectionMenu = null
            }
        }
    }

    private fun animateMenuDismiss() {
        menuShowLock.lock()
        try {
            isMenuAnimating.set(true)
            textSelectionMenu?.let { popup ->
                try {
                    // 获取PopupWindow的内容视图
                    val contentView = popup.contentView
                    if (contentView != null && contentView.isAttachedToWindow) {
                        // 使用内容视图进行动画，而不是PopupWindow本身
                        contentView.animate()
                            .alpha(0f)
                            .scaleX(0.8f)
                            .scaleY(0.8f)
                            .setDuration(150)
                            .setInterpolator(AccelerateInterpolator())
                            .withEndAction {
                                dismissPopupSafely(popup)
                                TextSelectionMenuController.cleanupState()
                            }
                            .start()
                        
                        // 等待动画完成，设置超时防止死锁
                        if (!menuAnimationComplete.await(MENU_SHOW_TIMEOUT, TimeUnit.MILLISECONDS)) {
                            Log.w(TAG, "等待动画完成超时")
                            dismissPopupSafely(popup)
                            TextSelectionMenuController.cleanupState()
                        }
                    } else {
                        // 内容视图不存在或未附加到窗口，直接关闭弹出窗口
                        dismissPopupSafely(popup)
                        TextSelectionMenuController.cleanupState()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "隐藏文本选择菜单失败", e)
                    dismissPopupSafely(popup)
                    TextSelectionMenuController.cleanupState()
                }
            } ?: run {
                // 如果没有菜单视图，直接清理状态
                TextSelectionMenuController.cleanupState()
            }
        } finally {
            menuShowLock.unlock()
        }
        mainHandler.removeCallbacksAndMessages(null)
    }

    // 安全地关闭弹出窗口
    private fun dismissPopupSafely(popup: PopupWindow) {
        try {
            if (popup.isShowing) {
                popup.dismiss()
            }
        } catch (e: Exception) {
            Log.e(TAG, "关闭弹出窗口失败", e)
        }
        textSelectionMenu = null
    }

    // 计算菜单显示位置
    private fun calculateMenuPosition(webView: WebView, x: Int, y: Int, menuView: View): Point {
        // 测量菜单视图大小
        menuView.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        
        val menuWidth = menuView.measuredWidth
        val menuHeight = menuView.measuredHeight
        
        // 获取WebView在屏幕上的位置
        val webViewLocation = IntArray(2)
        webView.getLocationOnScreen(webViewLocation)
        
        // 屏幕尺寸
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        
        // 计算菜单位置，优先显示在选择点上方
        // x坐标：优先水平居中于点击位置，但不超出屏幕边界
        val menuX = max(0, min(x - menuWidth / 2, screenWidth - menuWidth))
        
        // y坐标：优先在点击位置上方45dp，如果太靠上则放在点击位置下方25dp
        val offsetUp = dpToPx(45)
        val offsetDown = dpToPx(25)
        
        var menuY = y - menuHeight - offsetUp
        
        // 如果太靠上，或者与WebView顶部太近，放到触摸点下方
        if (menuY < 0 || (y - webViewLocation[1]) < menuHeight) {
            menuY = y + offsetDown
        }
        
        // 确保不超出屏幕底部
        if (menuY + menuHeight > screenHeight) {
            menuY = screenHeight - menuHeight - dpToPx(10)
        }
        
        Log.d(TAG, "菜单位置: ($menuX, $menuY), 屏幕: ${screenWidth}x${screenHeight}, 菜单: ${menuWidth}x${menuHeight}")
        return Point(menuX, menuY)
    }

    // 添加 JSBridge 接口类
    private inner class WebViewJSBridge(private val webView: WebView) {
        @JavascriptInterface
        fun onCopyText(text: String) {
            mainHandler.post {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("selected text", text)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this@DualFloatingWebViewService, "已复制", Toast.LENGTH_SHORT).show()
            }
        }

        @JavascriptInterface
        fun onShareText(text: String) {
            mainHandler.post {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(Intent.createChooser(intent, "分享文本").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }

        @JavascriptInterface
        fun onSearchText(text: String) {
            mainHandler.post {
                // 获取当前搜索引擎设置
                val leftEngine = settingsManager.getLeftWindowSearchEngine()
                val centerEngine = settingsManager.getCenterWindowSearchEngine()
                val rightEngine = settingsManager.getRightWindowSearchEngine()

                // 根据窗口数量执行搜索
                when (windowCount) {
                    1 -> {
                        firstWebView?.loadUrl(getSearchUrl(leftEngine, text))
                    }
                    2 -> {
                        firstWebView?.loadUrl(getSearchUrl(leftEngine, text))
                        secondWebView?.loadUrl(getSearchUrl(centerEngine, text))
                    }
                    else -> {
                        firstWebView?.loadUrl(getSearchUrl(leftEngine, text))
                        secondWebView?.loadUrl(getSearchUrl(centerEngine, text))
                        thirdWebView?.loadUrl(getSearchUrl(rightEngine, text))
                    }
                }
                
                Toast.makeText(this@DualFloatingWebViewService, "正在搜索: $text", Toast.LENGTH_SHORT).show()
            }
        }

        @JavascriptInterface
        fun onTranslateText(text: String) {
            mainHandler.post {
                val url = "https://translate.google.com/?text=${URLEncoder.encode(text, "UTF-8")}"
                webView.loadUrl(url)
            }
        }
    }

    /**
     * 创建图标背景Drawable
     */
    private fun createIconBackgroundDrawables() {
        try {
            // 如果资源已存在，跳过创建
            resources.getIdentifier("engine_icon_background", "drawable", packageName)
            resources.getIdentifier("engine_icon_selected", "drawable", packageName)
            return
        } catch (e: Exception) {
            // 资源不存在，需要创建
        }
        
        try {
            // 创建临时目录
            val drawableDir = JavaFile(cacheDir, "drawable")
            if (!drawableDir.exists()) {
                drawableDir.mkdirs()
            }
            
            // 创建普通背景
            val normalBackgroundXml = """
                <?xml version="1.0" encoding="utf-8"?>
                <ripple xmlns:android="http://schemas.android.com/apk/res/android"
                    android:color="#33000000">
                    <item>
                        <shape android:shape="oval">
                            <solid android:color="#FFFFFF" />
                            <stroke android:color="#E0E0E0" android:width="1dp" />
                            <padding android:left="1dp" android:top="1dp" 
                                android:right="1dp" android:bottom="1dp" />
                        </shape>
                    </item>
                </ripple>
            """.trimIndent()
            
            // 创建选中背景
            val selectedBackgroundXml = """
                <?xml version="1.0" encoding="utf-8"?>
                <ripple xmlns:android="http://schemas.android.com/apk/res/android"
                    android:color="#33000000">
                    <item>
                        <shape android:shape="oval">
                            <solid android:color="#F2F8FF" />
                            <stroke android:color="#4285F4" android:width="1.5dp" />
                            <padding android:left="1dp" android:top="1dp" 
                                android:right="1dp" android:bottom="1dp" />
                        </shape>
                    </item>
                </ripple>
            """.trimIndent()
            
            // 保存为临时文件
            val normalFile = JavaFile(drawableDir, "engine_icon_background.xml")
            normalFile.writeText(normalBackgroundXml)
            
            val selectedFile = JavaFile(drawableDir, "engine_icon_selected.xml")
            selectedFile.writeText(selectedBackgroundXml)
            
            // 通过反射注册drawable
            val resourceClass = R.drawable::class.java
            val drawableField = resourceClass.getDeclaredField("engine_icon_background")
            drawableField.isAccessible = true
            drawableField.set(null, R.drawable.icon_background)
            
            val selectedField = resourceClass.getDeclaredField("engine_icon_selected")
            selectedField.isAccessible = true
            selectedField.set(null, R.drawable.icon_background_selected)
            
        } catch (e: Exception) {
            Log.e(TAG, "创建Drawable失败: ${e.message}")
        }
    }

    /**
     * 加载搜索引擎图标
     */
    private fun loadEngineIcon(iconView: ImageView, engine: SearchEngine) {
        try {
            // 从URL中提取域名
            val baseUrl = engine.url.split("?")[0]
            val uri = Uri.parse(baseUrl)
            var host = uri.host ?: run {
                // 如果无法获取主机名，使用默认图标
                Log.e(TAG, "无法获取主机名，URL: ${engine.url}")
                iconView.setImageResource(R.drawable.ic_search)
                return
            }
            
            // 特殊处理一些AI搜索引擎的图标域名
            val originalHost = host
            when {
                engine.name.contains("ChatGPT", ignoreCase = true) || 
                engine.name.contains("GPT-4", ignoreCase = true) || 
                engine.name.contains("OpenAI", ignoreCase = true) -> {
                    host = "openai.com"
                }
                engine.name.contains("Claude", ignoreCase = true) -> {
                    host = "anthropic.com"
                }
                engine.name.contains("文心", ignoreCase = true) || 
                engine.name.contains("一言", ignoreCase = true) -> {
                    host = "baidu.com"
                }
                engine.name.contains("通义", ignoreCase = true) || 
                engine.name.contains("千问", ignoreCase = true) -> {
                    host = "aliyun.com"
                }
                engine.name.contains("讯飞", ignoreCase = true) || 
                engine.name.contains("星火", ignoreCase = true) -> {
                    host = "xfyun.cn"
                }
                engine.name.contains("必应", ignoreCase = true) || 
                engine.name.contains("Bing", ignoreCase = true) ||
                engine.name.contains("Copilot", ignoreCase = true) -> {
                    host = "bing.com"
                }
                engine.name.contains("Gemini", ignoreCase = true) -> {
                    host = "google.com"
                }
                engine.name.contains("搜狗", ignoreCase = true) && engine.name.contains("AI", ignoreCase = true) -> {
                    host = "sogou.com"
                }
                engine.name.contains("Perplexity", ignoreCase = true) -> {
                    host = "perplexity.ai"
                }
            }
            
            if (originalHost != host) {
                Log.d(TAG, "图标域名映射: ${engine.name} - $originalHost -> $host")
            }
            
            // 构建Google图标服务URL
            val iconUrl = "https://www.google.com/s2/favicons?domain=$host&sz=64"
            Log.d(TAG, "加载图标: ${engine.name} - $iconUrl")
            
            // 使用异步线程加载图标
            Thread {
                try {
                    // 检查缓存
                    val iconCacheDir = JavaFile(cacheDir, "engine_icons")
                    if (!iconCacheDir.exists()) {
                        iconCacheDir.mkdirs()
                    }
                    
                    val cacheFileName = "${engine.name}_${host.replace(".", "_")}.png"
                    val iconFile = JavaFile(iconCacheDir, cacheFileName)
                    
                    // 如果缓存存在且未过期，直接使用缓存
                    if (iconFile.exists() && 
                       System.currentTimeMillis() - iconFile.lastModified() < TimeUnit.DAYS.toMillis(1)) {
                        val bitmap = BitmapFactory.decodeFile(iconFile.absolutePath)
                        if (bitmap != null) {
                            Log.d(TAG, "使用缓存图标: ${engine.name}")
                            mainHandler.post {
                                iconView.setImageBitmap(bitmap)
                                iconView.visibility = View.VISIBLE
                            }
                            return@Thread
                        } else {
                            Log.e(TAG, "缓存图标解码失败: ${engine.name}")
                            iconFile.delete() // 删除无效缓存
                        }
                    }
                    
                    // 下载图标
                    Log.d(TAG, "开始下载图标: ${engine.name}")
                    val url = URL(iconUrl)
                    val connection = url.openConnection() as HttpURLConnection
                    connection.connectTimeout = 5000
                    connection.readTimeout = 5000
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                    
                    val responseCode = connection.responseCode
                    if (responseCode != HttpURLConnection.HTTP_OK) {
                        Log.e(TAG, "图标下载失败，HTTP错误: $responseCode - ${engine.name}")
                        mainHandler.post {
                            iconView.setImageResource(R.drawable.ic_search)
                        }
                        return@Thread
                    }
                    
                    val inputStream = connection.inputStream
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    
                    if (bitmap != null) {
                        Log.d(TAG, "图标下载成功: ${engine.name}")
                        // 保存到缓存
                        val outStream = FileOutputStream(iconFile)
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outStream)
                        outStream.flush()
                        outStream.close()
                        
                        // 更新UI
                        mainHandler.post {
                            iconView.setImageBitmap(bitmap)
                            iconView.visibility = View.VISIBLE
                        }
                    } else {
                        // 解码失败，使用默认图标
                        Log.e(TAG, "图标解码失败: ${engine.name}")
                        mainHandler.post {
                            iconView.setImageResource(R.drawable.ic_search)
                            iconView.visibility = View.VISIBLE
                        }
                    }
                    
                } catch (e: Exception) {
                    Log.e(TAG, "加载图标失败: ${engine.name} - ${e.message}", e)
                    // 加载失败时使用默认图标
                    mainHandler.post {
                        iconView.setImageResource(R.drawable.ic_search)
                        iconView.visibility = View.VISIBLE
                    }
                }
            }.start()
            
        } catch (e: Exception) {
            Log.e(TAG, "处理图标URL失败: ${engine.name} - ${e.message}", e)
            iconView.setImageResource(R.drawable.ic_search)
            iconView.visibility = View.VISIBLE
        }
    }

    /**
     * 更新视图可见性
     */
    private fun updateViewVisibilityByWindowCount() {
        Log.d(TAG, "更新视图可见性，窗口数量: $windowCount")
        
        // 获取WebView容器
        val webviewContainer = container?.findViewById<LinearLayout>(R.id.dual_webview_container)
        
        when (windowCount) {
            1 -> {
                // 只显示第一个窗口
                firstWebView?.visibility = View.VISIBLE
                divider1?.visibility = View.GONE
                secondWebView?.visibility = View.GONE
                divider2?.visibility = View.GONE
                thirdWebView?.visibility = View.GONE
                
                // 获取父容器
                val secondContainer = secondWebView?.parent as? View
                val thirdContainer = thirdWebView?.parent as? View
                secondContainer?.visibility = View.GONE
                thirdContainer?.visibility = View.GONE
                
                // 更新搜索引擎容器可见性
                firstEngineContainer?.visibility = View.VISIBLE
                secondEngineContainer?.visibility = View.GONE
                thirdEngineContainer?.visibility = View.GONE
                
                // 隐藏切换按钮，不再需要
                firstEngineToggle?.visibility = View.GONE
                secondEngineToggle?.visibility = View.GONE
                thirdEngineToggle?.visibility = View.GONE
                
                // 移除多余的容器
                webviewContainer?.let {
                    // 保留必要的视图
                    val viewsToKeep = mutableListOf<View>()
                    for (i in 0 until it.childCount) {
                        val child = it.getChildAt(i)
                        if (child == firstWebView?.parent) {
                            viewsToKeep.add(child)
                        }
                    }
                    
                    // 清空容器并重新添加需要的视图
                    it.removeAllViews()
                    for (view in viewsToKeep) {
                        it.addView(view)
                    }
                }
            }
            2 -> {
                // 显示两个窗口
                firstWebView?.visibility = View.VISIBLE
                divider1?.visibility = View.VISIBLE
                secondWebView?.visibility = View.VISIBLE
                divider2?.visibility = View.GONE
                thirdWebView?.visibility = View.GONE
                
                // 获取父容器
                val secondContainer = secondWebView?.parent as? View
                val thirdContainer = thirdWebView?.parent as? View
                secondContainer?.visibility = View.VISIBLE
                thirdContainer?.visibility = View.GONE
                
                // 更新搜索引擎容器可见性
                firstEngineContainer?.visibility = View.VISIBLE
                secondEngineContainer?.visibility = View.VISIBLE
                thirdEngineContainer?.visibility = View.GONE
                
                // 隐藏切换按钮，不再需要
                firstEngineToggle?.visibility = View.GONE
                secondEngineToggle?.visibility = View.GONE
                thirdEngineToggle?.visibility = View.GONE
                
                // 移除多余的容器
                webviewContainer?.let {
                    // 保留必要的视图
                    val viewsToKeep = mutableListOf<View>()
                    for (i in 0 until it.childCount) {
                        val child = it.getChildAt(i)
                        if (child == firstWebView?.parent || 
                            child == divider1 || 
                            child == secondWebView?.parent) {
                            viewsToKeep.add(child)
                        }
                    }
                    
                    // 清空容器并重新添加需要的视图
                    it.removeAllViews()
                    for (view in viewsToKeep) {
                        it.addView(view)
                    }
                }
            }
            else -> {
                // 显示全部三个窗口
                firstWebView?.visibility = View.VISIBLE
                divider1?.visibility = View.VISIBLE
                secondWebView?.visibility = View.VISIBLE
                divider2?.visibility = View.VISIBLE
                thirdWebView?.visibility = View.VISIBLE
                
                // 获取父容器
                val secondContainer = secondWebView?.parent as? View
                val thirdContainer = thirdWebView?.parent as? View
                secondContainer?.visibility = View.VISIBLE
                thirdContainer?.visibility = View.VISIBLE
                
                // 更新搜索引擎容器可见性
                firstEngineContainer?.visibility = View.VISIBLE
                secondEngineContainer?.visibility = View.VISIBLE
                thirdEngineContainer?.visibility = View.VISIBLE
                
                // 隐藏切换按钮，不再需要
                firstEngineToggle?.visibility = View.GONE
                secondEngineToggle?.visibility = View.GONE
                thirdEngineToggle?.visibility = View.GONE
                
                // 移除多余的容器
                webviewContainer?.let {
                    // 保留必要的视图
                    val viewsToKeep = mutableListOf<View>()
                    for (i in 0 until it.childCount) {
                        val child = it.getChildAt(i)
                        if (child == firstWebView?.parent || 
                            child == divider1 || 
                            child == secondWebView?.parent ||
                            child == divider2 ||
                            child == thirdWebView?.parent) {
                            viewsToKeep.add(child)
                        }
                    }
                    
                    // 清空容器并重新添加需要的视图
                    it.removeAllViews()
                    for (view in viewsToKeep) {
                        it.addView(view)
                    }
                }
            }
        }
        
        // 确保两种搜索引擎同时可见
        if (firstAIEngineContainer != null && firstEngineContainer != null) {
            firstAIScrollContainer?.visibility = View.VISIBLE
            firstEngineContainer?.visibility = View.VISIBLE
            
            // 确保AI搜索引擎按钮已创建
            if (firstWebView != null && firstAIEngineContainer?.childCount == 0) {
                createAIEngineButtons(firstWebView!!, firstAIEngineContainer as LinearLayout, "left") { key ->
                    leftEngineKey = key
                    settingsManager.setLeftWindowSearchEngine(key)
                }
            }
        }
        
        if (secondAIEngineContainer != null && secondEngineContainer != null && windowCount >= 2) {
            secondAIScrollContainer?.visibility = View.VISIBLE
            secondEngineContainer?.visibility = View.VISIBLE
            
            // 确保AI搜索引擎按钮已创建
            if (secondWebView != null && secondAIEngineContainer?.childCount == 0) {
                createAIEngineButtons(secondWebView!!, secondAIEngineContainer as LinearLayout, "center") { key ->
                    centerEngineKey = key
                    settingsManager.setCenterWindowSearchEngine(key)
                }
            }
        }
        
        if (thirdAIEngineContainer != null && thirdEngineContainer != null && windowCount >= 3) {
            thirdAIScrollContainer?.visibility = View.VISIBLE
            thirdEngineContainer?.visibility = View.VISIBLE
            
            // 确保AI搜索引擎按钮已创建
            if (thirdWebView != null && thirdAIEngineContainer?.childCount == 0) {
                createAIEngineButtons(thirdWebView!!, thirdAIEngineContainer as LinearLayout, "right") { key ->
                    rightEngineKey = key
                    settingsManager.setRightWindowSearchEngine(key)
                }
            }
        }
        
        // 更新布局
        container?.requestLayout()
    }

    /**
     * 刷新搜索引擎容器设置
     * 注意：不再使用切换按钮，改为同时显示两种搜索引擎
     */
    private fun refreshEngineToggleButtons() {
        Log.d(TAG, "刷新搜索引擎容器设置")
        
        // 隐藏所有切换按钮，不再需要
        firstEngineToggle?.visibility = View.GONE
        secondEngineToggle?.visibility = View.GONE
        thirdEngineToggle?.visibility = View.GONE
        
        // 确保两种搜索引擎都可见
        firstAIScrollContainer?.visibility = View.VISIBLE
        firstEngineContainer?.visibility = View.VISIBLE
        
        secondAIScrollContainer?.visibility = if (windowCount >= 2) View.VISIBLE else View.GONE
        secondEngineContainer?.visibility = if (windowCount >= 2) View.VISIBLE else View.GONE
        
        thirdAIScrollContainer?.visibility = if (windowCount >= 3) View.VISIBLE else View.GONE
        thirdEngineContainer?.visibility = if (windowCount >= 3) View.VISIBLE else View.GONE
        
        // 确保AI搜索引擎按钮已创建
        firstWebView?.let { webView ->
            firstAIEngineContainer?.let { container ->
                if (container.childCount == 0) {
                    createAIEngineButtons(webView, container as LinearLayout, "left") { key ->
                        leftEngineKey = key
                        settingsManager.setLeftWindowSearchEngine(key)
                    }
                }
            }
        }
        
        secondWebView?.let { webView ->
            secondAIEngineContainer?.let { container ->
                if (container.childCount == 0) {
                    createAIEngineButtons(webView, container as LinearLayout, "center") { key ->
                        centerEngineKey = key
                        settingsManager.setCenterWindowSearchEngine(key)
                    }
                }
            }
        }
        
        thirdWebView?.let { webView ->
            thirdAIEngineContainer?.let { container ->
                if (container.childCount == 0) {
                    createAIEngineButtons(webView, container as LinearLayout, "right") { key ->
                        rightEngineKey = key
                        settingsManager.setRightWindowSearchEngine(key)
                    }
                }
            }
        }
        
        Log.d(TAG, "搜索引擎容器设置刷新完成")
    }

    /**
     * 设置搜索引擎滚动视图的触摸事件处理
     * 确保搜索引擎区域可以独立滚动
     */
    private fun setupEngineScrollViews() {
        try {
            // 获取所有搜索引擎滚动视图
            val engineScrolls = listOfNotNull(
                floatingView?.findViewById<HorizontalScrollView>(R.id.first_engine_scroll),
                floatingView?.findViewById<HorizontalScrollView>(R.id.second_engine_scroll),
                floatingView?.findViewById<HorizontalScrollView>(R.id.third_engine_scroll)
            )
            
            val aiScrolls = listOfNotNull(
                floatingView?.findViewById<HorizontalScrollView>(R.id.first_ai_scroll_container),
                floatingView?.findViewById<HorizontalScrollView>(R.id.second_ai_scroll_container),
                floatingView?.findViewById<HorizontalScrollView>(R.id.third_ai_scroll_container)
            )
            
            // 所有滚动视图列表
            val allScrolls = engineScrolls + aiScrolls
            
            // 为每个滚动视图设置触摸事件
            allScrolls.forEach { scrollView ->
                // 设置滚动条可见
                scrollView.isHorizontalScrollBarEnabled = true
                
                // 设置触摸监听
                scrollView.setOnTouchListener { v, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            // 找到主HorizontalScrollView
                            var parent = v.parent
                            var mainScrollView: HorizontalScrollView? = null
                            
                            // 向上遍历父视图树
                            while (parent != null) {
                                if (parent is HorizontalScrollView && parent != v) {
                                    mainScrollView = parent
                                    break
                                }
                                parent = parent.parent
                            }
                            
                            // 禁止主滚动视图拦截触摸事件
                            mainScrollView?.requestDisallowInterceptTouchEvent(true)
                            
                            // 显式设置一个标记，表明我们正在处理搜索引擎区域的滚动
                            v.tag = "ENGINE_SCROLLING"
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            // 重置标记
                            v.tag = null
                            
                            // 恢复所有父视图的事件拦截
                            var parent = v.parent
                            while (parent != null) {
                                if (parent is HorizontalScrollView) {
                                    parent.requestDisallowInterceptTouchEvent(false)
                                }
                                parent = parent.parent
                            }
                        }
                    }
                    // 返回false继续传递事件
                    false
                }
            }
            
            // 此外，为搜索引擎图标添加更宽松的触摸区域
            val iconContainers = listOfNotNull(
                floatingView?.findViewById<View>(R.id.first_engine_container),
                floatingView?.findViewById<View>(R.id.second_engine_container),
                floatingView?.findViewById<View>(R.id.third_engine_container),
                floatingView?.findViewById<View>(R.id.first_ai_engine_container),
                floatingView?.findViewById<View>(R.id.second_ai_engine_container),
                floatingView?.findViewById<View>(R.id.third_ai_engine_container)
            )
            
            iconContainers.forEach { container ->
                container.setOnTouchListener { v, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            // 向上寻找HorizontalScrollView父视图
                            var parent = v.parent
                            while (parent != null && parent !is HorizontalScrollView) {
                                parent = parent.parent
                            }
                            
                            // 如果找到了HorizontalScrollView父视图，告诉它不要拦截触摸事件
                            (parent as? HorizontalScrollView)?.requestDisallowInterceptTouchEvent(true)
                        }
                    }
                    // 返回false继续传递事件
                    false
                }
            }
            
            Log.d(TAG, "已设置搜索引擎滚动视图的触摸事件处理，保证独立滚动")
        } catch (e: Exception) {
            Log.e(TAG, "设置搜索引擎滚动视图的触摸事件处理失败", e)
        }
    }

    /**
     * 初始化设置
     */
    private fun initializeSettings() {
        // 从设置管理器获取配置
        settingsManager = SettingsManager.getInstance(this)
        
        // 获取窗口数量
        windowCount = settingsManager.getDefaultWindowCount()
        Log.d(TAG, "从设置中获取窗口数量: $windowCount")
        
        // 获取搜索引擎设置
        leftEngineKey = settingsManager.getLeftWindowSearchEngine()
        centerEngineKey = settingsManager.getCenterWindowSearchEngine()
        rightEngineKey = settingsManager.getRightWindowSearchEngine()
        Log.d(TAG, "搜索引擎设置 - 左: $leftEngineKey, 中: $centerEngineKey, 右: $rightEngineKey")
        
        // 获取布局方向
        isHorizontalLayout = sharedPrefs.getBoolean(KEY_IS_HORIZONTAL, true)
        
        // 更新窗口计数显示
        windowCountToggleView?.text = windowCount.toString()
        Log.d(TAG, "设置窗口计数显示: $windowCount")
        
        // 根据窗口数量更新视图显示
        updateViewVisibilityByWindowCount()
        
        // 设置上方控制区域的触摸事件处理，防止WebView页面切换
        setupUpperControlsTouchEvents()
        
        // 设置主HorizontalScrollView的触摸事件处理，让其只响应WebView区域的滑动
        setupMainHorizontalScrollView()
        
        // 设置搜索引擎滚动视图的触摸事件处理
        setupEngineScrollViews()
        
        // 设置WebView区域和搜索引擎区域独立滚动
        setupIndependentScrolling()
        
        // 设置WebView触摸事件处理
        setupWebViewTouchHandling()
    }
    
    /**
     * 设置主HorizontalScrollView的触摸事件处理
     * 让其只响应WebView区域的滑动，上方区域的滑动不触发页面切换
     */
    private fun setupMainHorizontalScrollView() {
        try {
            // 查找根布局下的所有HorizontalScrollView
            val mainScrollView = findMainHorizontalScrollView()
            
            if (mainScrollView == null) {
                Log.e(TAG, "找不到主HorizontalScrollView，尝试直接拦截所有触摸事件")
                return
            }
            
            Log.d(TAG, "找到了主HorizontalScrollView，设置自定义触摸事件处理")
            
            // 将滚动条设置为可见
            mainScrollView.isHorizontalScrollBarEnabled = true
            // 启用平滑滚动
            mainScrollView.isSmoothScrollingEnabled = true
            // 设置滚动条样式
            mainScrollView.scrollBarStyle = HorizontalScrollView.SCROLLBARS_INSIDE_OVERLAY
            
            // 确保HorizontalScrollView可以接收触摸事件
            mainScrollView.isFocusable = true
            mainScrollView.isFocusableInTouchMode = true
            
            // 重要：移除任何可能阻止滚动的触摸监听器
            mainScrollView.setOnTouchListener(null)
            
            Log.d(TAG, "成功配置主HorizontalScrollView")
        } catch (e: Exception) {
            Log.e(TAG, "设置主HorizontalScrollView触摸事件失败: ${e.message}", e)
        }
    }
    
    /**
     * 查找主HorizontalScrollView
     * 尝试不同的方法找到包含WebView容器的滚动视图
     */
    private fun findMainHorizontalScrollView(): HorizontalScrollView? {
        try {
            // 尝试方法1：通过递归搜索视图树
            var mainScrollView = findHorizontalScrollViewInViewTree(floatingView)
            if (mainScrollView != null) return mainScrollView
            
            // 尝试方法2：检查任何包含LinearLayout(dual_webview_container)的HorizontalScrollView
            val webviewContainer = floatingView?.findViewById<LinearLayout>(R.id.dual_webview_container)
            if (webviewContainer != null) {
                var parent = webviewContainer.parent
                while (parent != null) {
                    if (parent is HorizontalScrollView) {
                        return parent
                    }
                    parent = parent.parent
                }
            }
            
            // 尝试方法3：直接在布局中寻找第一个HorizontalScrollView
            val view = floatingView
            if (view is ViewGroup) {
                return findFirstHorizontalScrollView(view)
            }
            
            Log.e(TAG, "无法找到主HorizontalScrollView")
            return null
        } catch (e: Exception) {
            Log.e(TAG, "查找主HorizontalScrollView失败: ${e.message}", e)
            return null
        }
    }
    
    /**
     * 在视图组中查找第一个HorizontalScrollView
     */
    private fun findFirstHorizontalScrollView(viewGroup: ViewGroup): HorizontalScrollView? {
        for (i in 0 until viewGroup.childCount) {
            val child = viewGroup.getChildAt(i)
            
            if (child is HorizontalScrollView) {
                return child
            }
            
            if (child is ViewGroup) {
                val result = findFirstHorizontalScrollView(child)
                if (result != null) {
                    return result
                }
            }
        }
        
        return null
    }
    
    /**
     * 递归搜索视图树中的HorizontalScrollView
     */
    private fun findHorizontalScrollViewInViewTree(view: View?): HorizontalScrollView? {
        if (view == null) return null
        
        if (view is HorizontalScrollView) {
            // 检查这个HorizontalScrollView是否包含webview_container
            val childCount = view.childCount
            for (i in 0 until childCount) {
                val child = view.getChildAt(i)
                if (child is LinearLayout && 
                    (child.id == R.id.dual_webview_container || 
                     containsWebViews(child))) {
                    return view
                }
            }
        }
        
        if (view is ViewGroup) {
            val childCount = view.childCount
            for (i in 0 until childCount) {
                val result = findHorizontalScrollViewInViewTree(view.getChildAt(i))
                if (result != null) return result
            }
        }
        
        return null
    }
    
    /**
     * 检查视图是否包含WebView
     */
    private fun containsWebViews(viewGroup: ViewGroup): Boolean {
        val childCount = viewGroup.childCount
        for (i in 0 until childCount) {
            val child = viewGroup.getChildAt(i)
            if (child is WebView) return true
            
            if (child is ViewGroup) {
                if (containsWebViews(child)) return true
            }
        }
        return false
    }

    /**
     * 设置上方控制区域的触摸事件处理
     * 防止在WebView上方区域的横向滑动触发WebView页面切换
     */
    private fun setupUpperControlsTouchEvents() {
        try {
            // 获取标题栏容器
            val firstTitleBar = floatingView?.findViewById<View>(R.id.first_title_bar)
            val secondTitleBar = floatingView?.findViewById<View>(R.id.second_title_bar)
            val thirdTitleBar = floatingView?.findViewById<View>(R.id.third_title_bar)
            
            // 获取搜索引擎容器
            val engineContainers = listOfNotNull(
                firstEngineContainer,
                secondEngineContainer,
                thirdEngineContainer
            )
            
            // 获取顶部工具栏和搜索栏
            val topControlBar = floatingView?.findViewById<View>(R.id.top_control_bar)
            val searchBar = floatingView?.findViewById<View>(R.id.search_bar)
            
            // 清除所有潜在的触摸监听，确保事件可以正常传递
            listOfNotNull(topControlBar, searchBar).forEach { view ->
                view.setOnTouchListener(null)
            }
            
            // 确保搜索引擎容器可以处理自己的触摸事件
            engineContainers.forEach { container ->
                container?.parent?.requestDisallowInterceptTouchEvent(false)
            }
            
            Log.d(TAG, "已重置上方控制区域的触摸事件处理")
        } catch (e: Exception) {
            Log.e(TAG, "设置上方区域触摸事件失败", e)
        }
    }

    /**
     * 保存当前搜索引擎设置
     */
    private fun saveSearchEngines() {
        try {
            // 保存用户当前的搜索引擎选择
            settingsManager.setLeftWindowSearchEngine(leftEngineKey)
            settingsManager.setCenterWindowSearchEngine(centerEngineKey)
            settingsManager.setRightWindowSearchEngine(rightEngineKey)
            
            Toast.makeText(this, "已保存搜索引擎设置", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "保存搜索引擎设置失败", e)
            Toast.makeText(this, "保存失败：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 切换到普通模式
     */
    private fun switchToNormal() {
        try {
            // 显示切换提示
            Toast.makeText(this, "正在切换到普通浏览模式", Toast.LENGTH_SHORT).show()
            
            // 停止当前服务
            stopSelf()
        } catch (e: Exception) {
            Log.e(TAG, "切换到普通模式失败: ${e.message}")
            Toast.makeText(this, "切换失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 切换窗口是否可获得焦点的标志，用于处理输入法
     */
    private fun toggleWindowFocusableFlag(focusable: Boolean) {
        try {
            val params = floatingView?.layoutParams as? WindowManager.LayoutParams ?: return
            
            if (focusable) {
                // 移除阻止获取焦点的标志
                params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
                // 移除阻止输入法的标志
                params.flags = params.flags and WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM.inv()
                // 设置输入法模式
                params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or
                        WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            } else {
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
            }
            
            // 应用新参数
            try {
                windowManager.updateViewLayout(floatingView, params)
                
                // 如果切换到可获取焦点，主动请求焦点并尝试显示输入法
                if (focusable) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        currentWebView?.let { webView ->
                            webView.requestFocus()
                            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                            imm.showSoftInput(webView, InputMethodManager.SHOW_FORCED)
                        }
                    }, 100)
                }
            } catch (e: Exception) {
                Log.e(TAG, "更新窗口布局参数失败", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "切换窗口焦点状态失败: ${e.message}")
        }
    }

    /**
     * 更新布局参数
     */
    private fun updateLayoutParams() {
        try {
            val displayMetrics = resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels
            
            val windowParams = floatingView?.layoutParams as? WindowManager.LayoutParams ?: return
            
            if (isHorizontalLayout) {
                windowParams.width = WindowManager.LayoutParams.MATCH_PARENT
                windowParams.height = (screenHeight * DEFAULT_HEIGHT_RATIO).toInt()
            } else {
                windowParams.width = WindowManager.LayoutParams.MATCH_PARENT
                windowParams.height = WindowManager.LayoutParams.MATCH_PARENT
            }
            
            try {
                windowManager.updateViewLayout(floatingView, windowParams)
            } catch (e: Exception) {
                Log.e(TAG, "更新窗口布局失败", e)
            }
            
            // 请求布局更新
            container?.requestLayout()
        } catch (e: Exception) {
            Log.e(TAG, "更新布局参数失败", e)
        }
    }

    /**
     * 根据窗口数量显示或隐藏相应的窗口
     */
    private fun updateWindowVisibility(windowCount: Int) {
        try {
            // 更新窗口数量变量
            this.windowCount = windowCount
            
            // 保存设置
            settingsManager.setDefaultWindowCount(windowCount)
            
            // 更新视图可见性
            updateViewVisibilityByWindowCount()
            
            // 更新窗口计数显示
            windowCountToggleView?.text = "$windowCount"
        } catch (e: Exception) {
            Log.e(TAG, "更新窗口可见性失败", e)
        }
    }

    /**
     * 设置独立滚动区域
     * 确保WebView区域和搜索引擎区域可以独立滚动
     */
    private fun setupIndependentScrolling() {
        try {
            // 获取主HorizontalScrollView (WebView容器的滚动视图)
            val mainScrollView = findMainHorizontalScrollView()
            if (mainScrollView == null) {
                Log.e(TAG, "找不到主HorizontalScrollView，无法设置独立滚动")
                return
            }
            
            // 查找所有搜索引擎滚动视图
            val engineScrolls = listOfNotNull(
                floatingView?.findViewById<HorizontalScrollView>(R.id.first_engine_scroll),
                floatingView?.findViewById<HorizontalScrollView>(R.id.second_engine_scroll),
                floatingView?.findViewById<HorizontalScrollView>(R.id.third_engine_scroll),
                floatingView?.findViewById<HorizontalScrollView>(R.id.first_ai_scroll_container),
                floatingView?.findViewById<HorizontalScrollView>(R.id.second_ai_scroll_container),
                floatingView?.findViewById<HorizontalScrollView>(R.id.third_ai_scroll_container)
            )

            // 获取所有搜索引擎图标容器
            val engineContainers = listOfNotNull(
                floatingView?.findViewById<LinearLayout>(R.id.first_engine_container),
                floatingView?.findViewById<LinearLayout>(R.id.second_engine_container),
                floatingView?.findViewById<LinearLayout>(R.id.third_engine_container)
            )
            
            // 设置主滚动视图对搜索引擎区域触摸的判断
            mainScrollView.setOnTouchListener { _, event ->
                var isEngineAreaTouch = false
                
                // 计算每个搜索引擎区域的位置，看触摸点是否在其中
                engineScrolls.forEach { scrollView ->
                    if (scrollView.visibility == View.VISIBLE) {
                        val location = IntArray(2)
                        scrollView.getLocationOnScreen(location)
                        val scrollRect = android.graphics.Rect(
                            location[0],
                            location[1],
                            location[0] + scrollView.width,
                            location[1] + scrollView.height
                        )
                        
                        if (scrollRect.contains(event.rawX.toInt(), event.rawY.toInt())) {
                            isEngineAreaTouch = true
                            // 如果触摸在引擎区域，禁止主滚动视图拦截
                            mainScrollView.requestDisallowInterceptTouchEvent(true)
                            return@forEach
                        }
                    }
                }
                
                // 如果不是引擎区域触摸，可以正常滚动
                !isEngineAreaTouch
            }
            
            // 为每个搜索引擎滚动视图添加触摸处理
            engineScrolls.forEach { scrollView ->
                scrollView.setOnTouchListener { v, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            // 禁止主滚动视图拦截
                            mainScrollView.requestDisallowInterceptTouchEvent(true)
                            // 记录触摸开始位置
                            v.tag = android.util.Pair(event.x, event.y)
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val touchData = v.tag as? android.util.Pair<Float, Float>
                            val startX = touchData?.first ?: 0f
                            val startY = touchData?.second ?: 0f
                            val deltaX = kotlin.math.abs(event.x - startX)
                            val deltaY = kotlin.math.abs(event.y - startY)
                            
                            // 如果水平移动明显大于垂直移动，确保自己处理而不是主滚动视图
                            if (deltaX > deltaY * 1.5f) {
                                mainScrollView.requestDisallowInterceptTouchEvent(true)
                            }
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            // 恢复主滚动视图的事件拦截
                            mainScrollView.requestDisallowInterceptTouchEvent(false)
                            // 清除标记
                            v.tag = null
                        }
                    }
                    // 返回false以允许事件继续传递给HorizontalScrollView本身
                    false
                }
            }

            // 为搜索引擎图标容器添加触摸处理
            engineContainers.forEach { container ->
                container.setOnTouchListener { v, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            // 禁止主滚动视图拦截事件，确保在图标区域滑动时不会触发WebView页面滑动
                            mainScrollView.requestDisallowInterceptTouchEvent(true)
                            // 记录初始触摸位置
                            v.tag = android.util.Pair(event.x, event.y)
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val touchData = v.tag as? android.util.Pair<Float, Float>
                            val startX = touchData?.first ?: 0f
                            val startY = touchData?.second ?: 0f
                            val deltaX = kotlin.math.abs(event.x - startX)
                            val deltaY = kotlin.math.abs(event.y - startY)
                            
                            // 在水平移动时保持对滚动的控制，不让主WebView区域拦截
                            if (deltaX > deltaY) {
                                mainScrollView.requestDisallowInterceptTouchEvent(true)
                                // 确保找到父级ScrollView并让它处理滚动
                                var parent = v.parent
                                while (parent != null) {
                                    if (parent is HorizontalScrollView) {
                                        parent.requestDisallowInterceptTouchEvent(false)
                                        break
                                    }
                                    parent = parent.parent
                                }
                            }
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            // 恢复正常事件处理
                            mainScrollView.requestDisallowInterceptTouchEvent(false)
                            v.tag = null
                        }
                    }
                    // 返回false让事件继续传递
                    false
                }
            }
            
            Log.d(TAG, "已设置WebView区域和搜索引擎区域独立滚动")
        } catch (e: Exception) {
            Log.e(TAG, "设置独立滚动区域失败", e)
        }
    }

    /**
     * 设置WebView触摸事件处理
     * 确保WebView内容区域可以独立处理触摸事件
     */
    private fun setupWebViewTouchHandling() {
        try {
            // 获取所有WebView
            val webViews = listOfNotNull(
                floatingView?.findViewById<WebView>(R.id.first_floating_webview),
                floatingView?.findViewById<WebView>(R.id.second_floating_webview),
                floatingView?.findViewById<WebView>(R.id.third_floating_webview)
            )
            
            // 获取主HorizontalScrollView
            val mainScrollView = findMainHorizontalScrollView()
            if (mainScrollView == null) {
                Log.e(TAG, "找不到主HorizontalScrollView，无法设置WebView触摸处理")
                return
            }
            
            // 为每个WebView设置触摸事件处理
            webViews.forEach { webView ->
                webView.setOnTouchListener { v, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            // 记录初始触摸位置
                            v.tag = android.util.Pair(event.x, event.y)
                            
                            // 允许主滚动视图拦截事件，因为这是WebView区域，我们希望在水平滑动时可以切换WebView页面
                            mainScrollView.requestDisallowInterceptTouchEvent(false)
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val touchData = v.tag as? android.util.Pair<Float, Float>
                            val startX = touchData?.first ?: 0f
                            val startY = touchData?.second ?: 0f
                            val deltaX = kotlin.math.abs(event.x - startX)
                            val deltaY = kotlin.math.abs(event.y - startY)
                            
                            // 如果是垂直滑动为主，或者WebView内容需要水平滚动，则让WebView处理事件
                            if (deltaY > deltaX || (deltaX > deltaY * 1.5f && webView.canScrollHorizontally(1))) {
                                // 如果WebView内容需要滚动，则禁止主滚动视图拦截
                                mainScrollView.requestDisallowInterceptTouchEvent(true)
                            } else {
                                // 水平滑动且WebView内容不需要水平滚动时，让主滚动视图处理事件（用于切换WebView页面）
                                mainScrollView.requestDisallowInterceptTouchEvent(false)
                            }
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            // 恢复主滚动视图的事件拦截
                            mainScrollView.requestDisallowInterceptTouchEvent(false)
                            // 清除标记
                            v.tag = null
                        }
                    }
                    // 返回false以允许事件继续传递给WebView本身
                    false
                }
            }
            
            Log.d(TAG, "已设置WebView触摸事件处理，确保内容区域可以独立处理触摸事件")
        } catch (e: Exception) {
            Log.e(TAG, "设置WebView触摸事件处理失败", e)
        }
    }

    /**
     * 在AI搜索引擎中执行搜索，并自动粘贴搜索内容并发送
     * 
     * 功能说明：
     * 1. 当用户点击AI搜索引擎图标时，自动将搜索框中的关键词粘贴到AI大模型的输入框中
     * 2. 自动点击发送按钮提交问题
     * 3. 支持多种常见AI大模型（ChatGPT、Claude、文心一言、通义千问、讯飞星火等）
     *
     * 实现原理：
     * 1. 首先加载AI搜索引擎的URL
     * 2. 将搜索关键词保存到剪贴板
     * 3. 等待页面加载完成后（延迟3秒），注入JavaScript脚本
     * 4. JavaScript脚本会查找输入框并填入搜索内容，然后模拟点击发送按钮
     * 5. 如果首次注入不成功，会在2秒后尝试再次注入
     * 
     * @param webView 要加载AI引擎的WebView对象
     * @param query 用户输入的搜索关键词
     * @param engineUrl AI搜索引擎的URL
     * @param engineName AI搜索引擎的名称，用于确定使用哪种注入脚本
     */
    private fun performAISearch(webView: WebView, query: String, engineUrl: String, engineName: String) {
        try {
            // 确保WebView可以获取焦点并激活输入法
            ensureWebViewFocusable(webView)
            
            // 记录查询文本到剪贴板
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("search query", query)
            clipboard.setPrimaryClip(clip)
            
            // 显示提示
            Toast.makeText(
                this@DualFloatingWebViewService,
                "正在准备加载AI搜索...",
                Toast.LENGTH_SHORT
            ).show()
            
            // 首先加载引擎URL
            val baseUrl = engineUrl.split("?")[0]
            webView.loadUrl(baseUrl)
            
            // 使用Handler延迟执行脚本注入，避免替换原有的WebViewClient
            Handler(Looper.getMainLooper()).postDelayed({
                // 确保WebView可以获取焦点并激活输入法
                ensureWebViewFocusable(webView)
                
                // 根据不同的AI引擎使用不同的注入脚本
                val script = when {
                    engineName.contains("文心", ignoreCase = true) -> {
                        """
                        (function() {
                            try {
                                console.log("开始注入文心一言脚本");
                                
                                // 文心一言的移动端适配选择器集合
                                var possibleInputs = [
                                    // 文心一言移动端布局
                                    '.chat-input textarea',
                                    '.chat-input input',
                                    '.text-input',
                                    'textarea.input-textarea',
                                    '.chat-inputarea textarea',
                                    'div[contenteditable="true"]',
                                    '.chat-input-area textarea',
                                    // 文心一言新版移动界面
                                    '.input-container textarea',
                                    '.bottom-area textarea',
                                    '#wenxin-input',
                                    // 新增选择器 - 2023版文心一言界面
                                    '.chat-toolbar textarea',
                                    '.bottom-input-area textarea',
                                    '.user-input-area textarea',
                                    '.chat-editor textarea',
                                    // 类名查找 - 更灵活的方式
                                    '[class*="input"]',
                                    '[class*="chat"] textarea',
                                    '[class*="text-area"]',
                                    '[class*="editor"]',
                                    // 通用后备选择器
                                    'textarea',
                                    'form textarea',
                                    'input[type="text"]',
                                    'div.textarea',
                                    '.text-area'
                                ];
                                
                                var textarea = null;
                                
                                // 尝试所有选择器，详细记录
                                console.log("开始查找输入框，总共 " + possibleInputs.length + " 个选择器");
                                for (var i = 0; i < possibleInputs.length; i++) {
                                    var elements = document.querySelectorAll(possibleInputs[i]);
                                    if (elements && elements.length > 0) {
                                        // 记录找到的所有元素
                                        console.log("找到选择器匹配: " + possibleInputs[i] + ", 元素数量: " + elements.length);
                                        
                                        // 输出元素详情，帮助调试
                                        for (var j = 0; j < elements.length; j++) {
                                            console.log("元素 #" + j + ": 类型=" + elements[j].tagName + 
                                                      ", 类名=" + elements[j].className + 
                                                      ", 可见=" + (elements[j].offsetParent !== null));
                                        }
                                        
                                        // 选择可见的最后一个元素（通常是当前活跃的输入框）
                                        for (var j = elements.length - 1; j >= 0; j--) {
                                            if (elements[j].offsetParent !== null) {
                                                textarea = elements[j];
                                                console.log("选择可见的元素 #" + j + " 作为输入框");
                                                break;
                                            }
                                        }
                                        
                                        // 如果没有找到可见元素，就选择最后一个
                                        if (!textarea) {
                                            textarea = elements[elements.length - 1];
                                            console.log("未找到可见元素，选择最后一个匹配元素");
                                        }
                                        
                                        break;
                                    }
                                }
                                
                                // 记录页面整体结构，帮助调试
                                var pageStructure = {
                                    title: document.title,
                                    url: window.location.href,
                                    bodyClasses: document.body.className,
                                    totalTextareas: document.querySelectorAll('textarea').length,
                                    totalInputs: document.querySelectorAll('input').length,
                                    totalButtons: document.querySelectorAll('button').length
                                };
                                console.log("页面结构：" + JSON.stringify(pageStructure));
                                
                                // 如果上面没找到，尝试查找包含特定类名部分的元素
                                if (!textarea) {
                                    console.log("通过常规选择器未找到输入框，尝试分析所有文本区域");
                                    var allTextareas = document.querySelectorAll('textarea');
                                    for (var i = 0; i < allTextareas.length; i++) {
                                        var element = allTextareas[i];
                                        console.log("文本区域 #" + i + ": 类名=" + element.className + 
                                                  ", ID=" + element.id + 
                                                  ", 可见=" + (element.offsetParent !== null) +
                                                  ", 尺寸=" + element.offsetWidth + "x" + element.offsetHeight);
                                        
                                        var classes = element.className.toLowerCase();
                                        if (classes.includes('input') || 
                                            classes.includes('chat') || 
                                            classes.includes('wenxin') ||
                                            classes.includes('text') ||
                                            classes.includes('editor')) {
                                            textarea = element;
                                            console.log("通过类名匹配找到文心一言输入框: " + classes);
                                            break;
                                        }
                                        
                                        // 检查位置 - 通常输入框在页面底部
                                        var rect = element.getBoundingClientRect();
                                        if (rect.bottom > window.innerHeight * 0.7 && element.offsetParent !== null) {
                                            textarea = element;
                                            console.log("通过位置（页面底部）找到可能的输入框");
                                            break;
                                        }
                                    }
                                }
                                
                                if (textarea) {
                                    console.log("找到文心一言输入框, 类型: " + textarea.tagName + ", 类名: " + textarea.className);
                                    
                                    // 1. 首先清空现有内容
                                    if (textarea.value) {
                                        textarea.value = '';
                                        console.log("清除现有内容");
                                    }
                                    
                                    // 2. 强制点击输入框以激活焦点 - 多点几次确保激活
                                    console.log("尝试多次点击以激活焦点");
                                    textarea.click();
                                    setTimeout(function() {
                                        textarea.click();
                                        
                                        // 3. 聚焦到输入框
                                        console.log("设置聚焦");
                                        textarea.focus();
                                        
                                        // 4. 延迟后开始处理文本内容
                                        setTimeout(function() {
                                            console.log("开始填充内容流程，搜索词: '$query'");
                                            
                                            // 直接设置值，不尝试粘贴命令
                                            console.log("使用直接设值方式");
                                            textarea.value = '$query';
                                            
                                            // 触发多种输入事件确保内容变化被检测
                                            textarea.dispatchEvent(new Event('input', { bubbles: true }));
                                            textarea.dispatchEvent(new Event('change', { bubbles: true }));
                                            
                                            // 检查是否设置成功
                                            console.log("设置后的值: " + textarea.value);
                                            
                                            // 如果仍未设置成功，再次尝试
                                            if (!textarea.value || textarea.value !== '$query') {
                                                console.log("再次尝试设置值");
                                                // 使用更激进的方式
                                                try {
                                                    // 使用 document.execCommand
                                                    textarea.select();
                                                    document.execCommand('insertText', false, '$query');
                                                    console.log("使用execCommand插入文本");
                                                } catch(e) {
                                                    console.log("execCommand失败: " + e);
                                                    // 继续尝试其他方法
                                                    textarea.innerHTML = '$query';
                                                    if (textarea.innerText !== undefined) {
                                                        textarea.innerText = '$query';
                                                    }
                                                }
                                                
                                                // 最后一次尝试：模拟键盘输入
                                                if (!textarea.value || textarea.value !== '$query') {
                                                    console.log("使用模拟键盘输入");
                                                    var text = '$query';
                                                    for (var i = 0; i < text.length; i++) {
                                                        var keyEvent = new KeyboardEvent('keypress', {
                                                            key: text[i],
                                                            code: 'Key' + text[i].toUpperCase(),
                                                            bubbles: true
                                                        });
                                                        textarea.dispatchEvent(keyEvent);
                                                    }
                                                }
                                            }
                                            
                                            // 再次触发事件，确保内容变更被检测
                                            console.log("再次触发输入事件");
                                            textarea.dispatchEvent(new Event('input', { bubbles: true }));
                                            textarea.dispatchEvent(new Event('change', { bubbles: true }));
                                            
                                            // 再次点击输入框，确保焦点
                                            textarea.click();
                                            textarea.focus();
                                            
                                            // 寻找并点击发送按钮
                                            setTimeout(function() {
                                                console.log("准备查找发送按钮");
                                                
                                                // 文心一言移动端发送按钮选择器
                                                var possibleButtons = [
                                                    '.chat-input button',
                                                    'button.submit-btn',
                                                    '.chat-submit',
                                                    '.send-btn',
                                                    'button.primary',
                                                    '.conversation-input-panel .submit',
                                                    // 新版UI选择器
                                                    '.input-container button',
                                                    '.input-submit',
                                                    '.send-button',
                                                    // 通用选择器
                                                    'form button[type="submit"]',
                                                    'button[aria-label="发送"]',
                                                    'button:not([disabled])',
                                                    'textarea + button',
                                                    // 通过位置寻找
                                                    '.bottom-area button',
                                                    '.chat-toolbar button',
                                                    // 查找所有按钮
                                                    'button'
                                                ];
                                                
                                                // 尝试找到发送按钮
                                                var sendButton = null;
                                                
                                                // 遍历所有可能的按钮选择器
                                                for (var i = 0; i < possibleButtons.length; i++) {
                                                    var buttons = document.querySelectorAll(possibleButtons[i]);
                                                    if (buttons && buttons.length > 0) {
                                                        console.log("找到按钮选择器: " + possibleButtons[i] + ", 数量: " + buttons.length);
                                                        
                                                        // 记录所有按钮信息
                                                        for (var j = 0; j < buttons.length; j++) {
                                                            var btn = buttons[j];
                                                            console.log("按钮 #" + j + ": 文本=" + btn.innerText + 
                                                                      ", 类名=" + btn.className + 
                                                                      ", 禁用=" + btn.disabled);
                                                        }
                                                        
                                                        // 尝试找到未禁用的按钮并且在textarea下方或右侧的按钮
                                                        for (var j = 0; j < buttons.length; j++) {
                                                            if (!buttons[j].disabled) {
                                                                // 判断按钮位置
                                                                var btnRect = buttons[j].getBoundingClientRect();
                                                                var textareaRect = textarea.getBoundingClientRect();
                                                                
                                                                // 在输入框下方或右侧的按钮更可能是发送按钮
                                                                if (btnRect.top >= textareaRect.top || 
                                                                    btnRect.left > textareaRect.right) {
                                                                    sendButton = buttons[j];
                                                                    console.log("选择位置合适的未禁用按钮 #" + j);
                                                                    break;
                                                                }
                                                            }
                                                        }
                                                        
                                                        // 如果没有找到位置合适的按钮，就选择第一个未禁用的按钮
                                                        if (!sendButton) {
                                                            for (var j = 0; j < buttons.length; j++) {
                                                                if (!buttons[j].disabled) {
                                                                    sendButton = buttons[j];
                                                                    console.log("选择第一个未禁用的按钮 #" + j);
                                                                    break;
                                                                }
                                                            }
                                                        }
                                                        
                                                        if (sendButton) break;
                                                    }
                                                }
                                                
                                                // 如果找到按钮，点击它
                                                if (sendButton) {
                                                    console.log("找到发送按钮，准备点击");
                                                    // 强制按钮变为可用状态
                                                    sendButton.disabled = false;
                                                    sendButton.removeAttribute('disabled');
                                                    
                                                    // 先模拟鼠标悬停
                                                    var hoverEvent = new MouseEvent('mouseover', {
                                                        bubbles: true,
                                                        cancelable: true,
                                                        view: window
                                                    });
                                                    sendButton.dispatchEvent(hoverEvent);
                                                    
                                                    // 然后点击
                                                    setTimeout(function() {
                                                        sendButton.click();
                                                        console.log("发送按钮已点击");
                                                        
                                                        // 再次点击，以防第一次没触发
                                                        setTimeout(function() {
                                                            sendButton.click();
                                                            console.log("发送按钮二次点击完成");
                                                        }, 300);
                                                    }, 200);
                                                } else {
                                                    console.log("未找到发送按钮，尝试回车键提交");
                                                    
                                                    // 如果没找到按钮，尝试回车键提交
                                                    var enterEvent = new KeyboardEvent('keydown', {
                                                        key: 'Enter',
                                                        code: 'Enter',
                                                        keyCode: 13,
                                                        which: 13,
                                                        bubbles: true
                                                    });
                                                    textarea.dispatchEvent(enterEvent);
                                                    console.log("已发送回车键事件");
                                                    
                                                    // 尝试提交包含输入框的表单
                                                    var form = textarea.closest('form');
                                                    if (form) {
                                                        console.log("找到表单，尝试提交");
                                                        form.submit();
                                                    }
                                                }
                                            }, 800);
                                        }, 600);
                                    }, 400);
                                } else {
                                    console.log("未找到文心一言输入框，记录页面结构");
                                    
                                    // 输出所有DOM元素类型统计，帮助调试
                                    var allElements = document.querySelectorAll('*');
                                    var elementTypes = {};
                                    for (var i = 0; i < allElements.length; i++) {
                                        var tag = allElements[i].tagName;
                                        elementTypes[tag] = (elementTypes[tag] || 0) + 1;
                                    }
                                    console.log("页面元素统计: " + JSON.stringify(elementTypes));
                                    
                                    // 尝试查找所有可能的输入元素
                                    var allInputs = document.querySelectorAll('input, textarea, [contenteditable="true"]');
                                    console.log("找到 " + allInputs.length + " 个可能的输入元素");
                                    
                                    // 输出页面结构以便调试
                                    var debugInfo = {
                                        textareas: document.querySelectorAll('textarea').length,
                                        inputs: document.querySelectorAll('input').length,
                                        buttons: document.querySelectorAll('button').length,
                                        forms: document.querySelectorAll('form').length,
                                        url: window.location.href,
                                        title: document.title
                                    };
                                    console.log("页面结构: " + JSON.stringify(debugInfo));
                                }
                            } catch(e) {
                                console.error("文心一言脚本执行错误: " + e);
                            }
                            
                            return "文心一言脚本执行完成";
                        })();
                        """
                    }
                    // ... 其他引擎的代码保持不变 ...
                    else -> {
                        // 通用脚本，尝试查找常见的输入框和提交按钮
                        """
                        (function() {
                            try {
                                console.log("开始注入通用AI脚本");
                                
                                // 尝试常见的输入框选择器
                                var possibleInputs = [
                                    'textarea', 
                                    '[contenteditable="true"]',
                                    '.ProseMirror',
                                    '.chat-input textarea',
                                    '.chat-input input',
                                    'input[type="text"]',
                                    '.input-area',
                                    '.prompt-input',
                                    '#searchbox',
                                    '#prompt-textarea'
                                ];
                                
                                var textarea = null;
                                
                                // 尝试所有可能的输入框选择器
                                for (var i = 0; i < possibleInputs.length; i++) {
                                    var input = document.querySelector(possibleInputs[i]);
                                    if (input) {
                                        textarea = input;
                                        console.log("找到输入框: " + possibleInputs[i]);
                                        break;
                                    }
                                }
                                
                                if (textarea) {
                                    // 强制点击输入框以激活焦点
                                    textarea.click();
                                    textarea.focus();
                                    
                                    setTimeout(function() {
                                        // 直接设置值，而不是尝试粘贴
                                        console.log("直接设置值: $query");
                                        
                                        // 根据元素类型设置值
                                        if (textarea.tagName === 'TEXTAREA' || textarea.tagName === 'INPUT') {
                                            textarea.value = '$query';
                                        } else {
                                            // 针对contenteditable元素
                                            textarea.innerHTML = '$query';
                                            textarea.innerText = '$query';
                                        }
                                        
                                        // 触发输入事件
                                        textarea.dispatchEvent(new Event('input', { bubbles: true }));
                                        textarea.dispatchEvent(new Event('change', { bubbles: true }));
                                        
                                        // 再次点击确保焦点
                                        textarea.click();
                                        
                                        // 等待值设置完成后查找并点击发送按钮
                                        setTimeout(function() {
                                            // 尝试查找各种发送按钮
                                            var possibleButtons = [
                                                'button[aria-label="发送"]',
                                                'button[aria-label="Send"]',
                                                'button[data-testid="send-button"]',
                                                '.chat-input button',
                                                'button.send',
                                                'button.submit',
                                                'button[type="submit"]',
                                                'form button',
                                                '.send-button',
                                                '.submit-button',
                                                '#search-icon',
                                                'button.primary',
                                                'button[aria-label="Submit"]'
                                            ];
                                            
                                            var sendButton = null;
                                            
                                            // 尝试所有可能的按钮选择器
                                            for (var i = 0; i < possibleButtons.length; i++) {
                                                var button = document.querySelector(possibleButtons[i]);
                                                if (button && !button.disabled) {
                                                    sendButton = button;
                                                    console.log("找到发送按钮: " + possibleButtons[i]);
                                                    break;
                                                }
                                            }
                                            
                                            if (sendButton) {
                                                sendButton.click();
                                                console.log("发送按钮已点击");
                                            } else {
                                                console.log("未找到发送按钮或所有按钮都被禁用");
                                                
                                                // 尝试回车键提交
                                                var event = new KeyboardEvent('keydown', {
                                                    key: 'Enter',
                                                    code: 'Enter',
                                                    which: 13,
                                                    keyCode: 13,
                                                    bubbles: true
                                                });
                                                textarea.dispatchEvent(event);
                                                console.log("尝试使用回车键提交");
                                            }
                                        }, 800);
                                    }, 500);
                                } else {
                                    console.log("未找到任何输入框");
                                }
                            } catch(e) {
                                console.error("通用AI脚本执行错误: " + e);
                            }
                        })();
                        """
                    }
                }
                
                // 执行脚本
                webView.evaluateJavascript(script) { result ->
                    Log.d(TAG, "AI引擎自动填充脚本执行结果: $result")
                    
                    // 如果首次注入不成功，尝试再次注入
                    if (result == "null" || result.contains("undefined")) {
                        Handler(Looper.getMainLooper()).postDelayed({
                            Log.d(TAG, "第一次注入可能未成功，尝试第二次注入...")
                            
                            // 再次确保WebView可以获取焦点并激活输入法
                            ensureWebViewFocusable(webView)
                            
                            webView.evaluateJavascript(script) { secondResult ->
                                Log.d(TAG, "AI引擎自动填充脚本二次执行结果: $secondResult")
                                
                                // 如果二次注入也不成功，尝试第三次
                                if (secondResult == "null" || secondResult.contains("undefined")) {
                                    Handler(Looper.getMainLooper()).postDelayed({
                                        Log.d(TAG, "尝试最后一次注入...")
                                        
                                        // 最后一次尝试前，再次确保WebView可以获取焦点
                                        ensureWebViewFocusable(webView)
                                        
                                        webView.evaluateJavascript(script) { thirdResult ->
                                            Log.d(TAG, "AI引擎自动填充脚本三次执行结果: $thirdResult")
                                            
                                            // 如果三次注入都不成功，尝试第四次，延长等待时间
                                            if (thirdResult == "null" || thirdResult.contains("undefined")) {
                                                Handler(Looper.getMainLooper()).postDelayed({
                                                    Log.d(TAG, "最终尝试注入...")
                                                    ensureWebViewFocusable(webView)
                                                    webView.evaluateJavascript(script, null)
                                                }, 4000) // 最后一次等待4秒
                                            }
                                        }
                                    }, 3000) // 第三次等待3秒
                                }
                            }
                        }, 2500) // 第二次等待2.5秒
                    }
                }
                
                // 显示提示
                Toast.makeText(
                    this@DualFloatingWebViewService,
                    "正在为您自动填充搜索内容...",
                    Toast.LENGTH_SHORT
                ).show()
            }, 6000) // 延长初始等待时间到6秒，确保页面完全加载
            
        } catch (e: Exception) {
            Log.e(TAG, "执行AI搜索时出错: ${e.message}", e)
            // 如果出错，退回到普通搜索方式
            performSearch(webView, query, engineUrl)
        }
    }
    
    /**
     * 确保WebView可以获取焦点并激活输入法
     * 这是解决WebView无法激活输入法的关键函数
     */
    private fun ensureWebViewFocusable(webView: WebView) {
        try {
            Log.d(TAG, "开始设置WebView焦点和输入法...")
            
            // 1. 确保WebView本身可以获取焦点
            webView.isFocusable = true
            webView.isFocusableInTouchMode = true
            
            // 2. 确保WebView的设置正确
            webView.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                saveFormData = true
                javaScriptCanOpenWindowsAutomatically = true
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                setGeolocationEnabled(true)
                loadsImagesAutomatically = true
                allowFileAccess = true
                allowContentAccess = true
                databaseEnabled = true
                setSupportMultipleWindows(true)
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                mediaPlaybackRequiresUserGesture = false
                defaultTextEncodingName = "UTF-8"
            }
            
            // 3. 注入输入法支持脚本
            webViewInputHelper.injectInputMethodSupport(webView)
            
            // 4. 修改窗口属性，确保可以激活输入法
            val params = floatingView?.layoutParams as? WindowManager.LayoutParams
            params?.let {
                // 移除阻止获取焦点的标志
                it.flags = it.flags and (WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE).inv()
                // 允许输入法
                it.flags = it.flags and (WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM).inv()
                
                // 增加输入相关标志
                it.flags = it.flags or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                
                // 设置输入法模式
                it.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                
                try {
                    windowManager.updateViewLayout(floatingView, it)
                    Log.d(TAG, "已更新窗口属性以允许输入法")
                } catch (e: Exception) {
                    Log.e(TAG, "更新窗口属性失败", e)
                }
            }
            
            // 4. 使WebView获取焦点并尝试激活输入法
            Handler(Looper.getMainLooper()).post {
                // 请求焦点
                webView.requestFocus()
                
                // 尝试显示输入法
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(webView, InputMethodManager.SHOW_IMPLICIT)
                
                // 确保页面内部的输入元素也获得焦点
                webView.evaluateJavascript("""
                    (function() {
                        // 查找所有可能的输入元素
                        var inputSelectors = [
                            'textarea', 
                            'input[type="text"]', 
                            '[contenteditable="true"]',
                            '.chat-input textarea',
                            '.text-input',
                            '.input-container textarea',
                            '[class*="input"]',
                            '.ProseMirror'
                        ];
                        
                        // 查找输入元素
                        var foundInputs = [];
                        for (var i = 0; i < inputSelectors.length; i++) {
                            var elements = document.querySelectorAll(inputSelectors[i]);
                            if (elements && elements.length > 0) {
                                for (var j = 0; j < elements.length; j++) {
                                    if (elements[j].offsetParent !== null) { // 只选择可见元素
                                        foundInputs.push(elements[j]);
                                    }
                                }
                            }
                        }
                        
                        console.log("找到 " + foundInputs.length + " 个可能的输入元素");
                        
                        // 尝试激活所有找到的输入元素
                        for (var i = 0; i < foundInputs.length; i++) {
                            try {
                                // 点击激活
                                foundInputs[i].click();
                                // 设置焦点
                                foundInputs[i].focus();
                                console.log("已尝试激活元素: " + foundInputs[i].tagName);
                                
                                // 如果是第一个元素，还尝试设置选中状态
                                if (i === 0) {
                                    if (foundInputs[i].select) {
                                        foundInputs[i].select();
                                    }
                                    
                                    // 尝试设置光标位置
                                    if (foundInputs[i].setSelectionRange) {
                                        foundInputs[i].setSelectionRange(0, 0);
                                    }
                                }
                            } catch (e) {
                                console.log("激活输入元素 #" + i + " 失败: " + e);
                            }
                        }
                        
                        // 如果没有找到输入元素，尝试在页面上创建一个临时输入框来触发输入法
                        if (foundInputs.length === 0) {
                            console.log("未找到可激活的输入元素，尝试创建临时输入框");
                            var tempInput = document.createElement('textarea');
                            tempInput.style.position = 'fixed';
                            tempInput.style.bottom = '0';
                            tempInput.style.left = '0';
                            tempInput.style.width = '100%';
                            tempInput.style.height = '50px';
                            tempInput.style.zIndex = '9999';
                            document.body.appendChild(tempInput);
                            
                            tempInput.focus();
                            
                            // 2秒后移除临时输入框
                            setTimeout(function() {
                                document.body.removeChild(tempInput);
                            }, 2000);
                            
                            return "已创建并激活临时输入框";
                        }
                        
                        return "已尝试激活 " + foundInputs.length + " 个输入元素";
                    })();
                """) { result ->
                    Log.d(TAG, "激活输入元素结果: $result")
                    
                    // 如果JavaScript激活不成功，再次尝试显示输入法
                    if (result == "null" || result.contains("0个")) {
                        Handler(Looper.getMainLooper()).postDelayed({
                            // 再次尝试请求焦点
                            webView.requestFocus()
                            imm.showSoftInput(webView, InputMethodManager.SHOW_FORCED)
                            
                            // 强制显示输入法
                            imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
                            
                            Log.d(TAG, "已强制显示输入法")
                        }, 1000)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "设置WebView焦点失败", e)
        }
    }
    
    /**
     * 处理文本选择时的JSON解析错误 - 已迁移到TextSelectionHelper类
     */
    private fun handleTextSelectionResult(result: String): JSONObject? {
        return TextSelectionHelper.handleTextSelectionResult(result)
    }

    private fun setupAIEngineContainer(container: LinearLayout, webView: WebView) {
        container.visibility = View.GONE
        container.setOnClickListener {
            isAIEngineActive = true
            switchToAIMode(webView)
            autoFillAndSendClipboardContent(webView)
        }
    }

    private fun autoFillAndSendClipboardContent(webView: WebView) {
        // 检查当前WebView是否处于AI模式
        if (isAIEngineActiveMap[webView] != true) return

        clipboardManager.primaryClip?.let { clipData ->
            if (clipData.itemCount > 0) {
                val clipText = clipData.getItemAt(0).text.toString()
                if (clipText.isNotEmpty()) {
                    // 记录日志
                    Log.d("DualFloatingWebView", "粘贴发送按钮被点击，内容: ${clipText.take(20)}${if (clipText.length > 20) "..." else ""}")
                    
                    // 显示Toast提示用户
                    Toast.makeText(
                        this,
                        "正在执行粘贴发送操作",
                        Toast.LENGTH_SHORT
                    ).show()
                    
                    // 使用JavaScript注入文本到输入框并触发发送
                    val escapedText = clipText.replace("'", "\\'").replace("\n", "\\n")
                    webView.evaluateJavascript("""
                        (function() {
                            console.log('开始执行粘贴发送操作');
                            try {
                                // 尝试查找可能的输入元素
                                const inputElement = 
                                    document.querySelector('textarea') || 
                                    document.querySelector('input[type="text"]') ||
                                    document.querySelector('[contenteditable="true"]') ||
                                    document.querySelector('.chat-input') ||
                                    document.querySelector('.input-area');
                                
                            if (inputElement) {
                                    console.log('找到输入元素:', inputElement);
                                    
                                    // 设置焦点
                                    inputElement.focus();
                                    
                                    // 尝试用不同方法设置值
                                inputElement.value = '$escapedText';
                                    
                                    // 如果是contenteditable元素
                                    if (inputElement.getAttribute('contenteditable') === 'true') {
                                        inputElement.innerHTML = '$escapedText';
                                    }
                                    
                                    // 触发输入事件
                                    const inputEvent = new Event('input', { bubbles: true });
                                    inputElement.dispatchEvent(inputEvent);
                                    
                                // 模拟按下回车键
                                    setTimeout(function() {
                                const enterEvent = new KeyboardEvent('keydown', {
                                    key: 'Enter',
                                    code: 'Enter',
                                    keyCode: 13,
                                    which: 13,
                                    bubbles: true
                                });
                                inputElement.dispatchEvent(enterEvent);
                                        
                                        // 寻找发送按钮并点击（作为后备方案）
                                        setTimeout(function() {
                                            const sendButton = 
                                                document.querySelector('button[type="submit"]') ||
                                                document.querySelector('.send-button') ||
                                                document.querySelector('[aria-label*="发送"]') ||
                                                document.querySelector('[aria-label*="send"]') ||
                                                document.querySelector('button:contains("发送")') ||
                                                document.querySelector('button:contains("Send")');
                                                
                                            if (sendButton) {
                                                console.log('找到发送按钮，点击它');
                                                sendButton.click();
                            }
                                        }, 300);
                                    }, 100);
                                    
                                    return true;
                                } else {
                                    console.log('找不到合适的输入元素');
                                    return false;
                                }
                            } catch(e) {
                                console.error('执行粘贴发送时出错:', e);
                                return false;
                            }
                        })();
                    """.trimIndent()) { result ->
                        Log.d("DualFloatingWebView", "粘贴发送执行结果: $result")
                    }
                }
            }
        }
    }

    private fun setupWebView(webView: WebView) {
        webView.settings.apply {
            // 基本设置
            javaScriptEnabled = true
            domStorageEnabled = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            
            // 增加输入相关设置
            saveFormData = true
            javaScriptCanOpenWindowsAutomatically = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            
            // 其他增强设置
            setGeolocationEnabled(true)
            loadsImagesAutomatically = true
            allowFileAccess = true
            allowContentAccess = true
            databaseEnabled = true
            setSupportMultipleWindows(true)
        }
        
        // 设置WebView基本属性
        webView.isFocusable = true
        webView.isFocusableInTouchMode = true
        webView.requestFocus()
        
        // 增强型触摸监听器
        webView.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // 确保窗口可以获取焦点
                    toggleWindowFocusableFlag(true)
                    
                    // 检查是否点击了输入框
                    webView.evaluateJavascript("""
                        (function() {
                            var x = ${event.x};
                            var y = ${event.y};
                            var element = document.elementFromPoint(x, y);
                            return element && (element.tagName === 'INPUT' || element.tagName === 'TEXTAREA' || element.contentEditable === 'true');
                        })();
                    """.trimIndent()) { result ->
                        if (result == "true") {
                            // 如果点击了输入框，强制显示输入法
                            Handler(Looper.getMainLooper()).post {
                                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                                imm.showSoftInput(webView, InputMethodManager.SHOW_FORCED)
                            }
                        }
                    }
                }
            }
            false
        }

        // 设置WebChromeClient
        webView.webChromeClient = object : android.webkit.WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                if (newProgress == 100) {
                    // 页面加载完成后注入增强型JavaScript
                    webView.evaluateJavascript("""
                        (function() {
                            // 监听所有可能的输入元素
                            document.addEventListener('click', function(e) {
                                if (e.target.tagName === 'INPUT' || 
                                    e.target.tagName === 'TEXTAREA' || 
                                    e.target.contentEditable === 'true' ||
                                    e.target.role === 'textbox') {
                                    e.target.focus();
                                    // 发送消息到Android
                                    window.androidInterface.onInputFieldClicked();
                                }
                            }, true);
                            
                            // 监听动态添加的元素
                            var observer = new MutationObserver(function(mutations) {
                                mutations.forEach(function(mutation) {
                                    if (mutation.addedNodes) {
                                        mutation.addedNodes.forEach(function(node) {
                                            if (node.nodeType === 1) {  // 元素节点
                                                var inputs = node.querySelectorAll('input, textarea, [contenteditable="true"]');
                                                inputs.forEach(function(input) {
                                                    input.addEventListener('click', function() {
                                                        this.focus();
                                                        window.androidInterface.onInputFieldClicked();
                                                    });
                                                });
                                            }
                                        });
                                    }
                                });
                            });
                            
                            observer.observe(document.body, {
                                childList: true,
                                subtree: true
                            });
                        })()
                    """.trimIndent(), null)
                }
            }
        }

        // 设置WebViewClient
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                
                // 注入CSS以优化输入体验
                webView.evaluateJavascript("""
                    (function() {
                        var style = document.createElement('style');
                        style.type = 'text/css';
                        style.innerHTML = `
                            input, textarea, [contenteditable="true"] {
                                font-size: 16px !important;
                                max-height: 999999px;
                                -webkit-user-select: text !important;
                                user-select: text !important;
                            }
                            body {
                                -webkit-tap-highlight-color: rgba(0,0,0,0);
                            }
                        `;
                        document.getElementsByTagName('head')[0].appendChild(style);
                    })()
                """.trimIndent(), null)
                
                if (isAIEngineActive && view == currentWebView) {
                    autoFillAndSendClipboardContent(webView)
                }
            }
        }
        
        // 添加JavaScript接口
        webView.addJavascriptInterface(object {
            @JavascriptInterface
            fun onInputFieldClicked() {
                Handler(Looper.getMainLooper()).post {
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.showSoftInput(webView, InputMethodManager.SHOW_FORCED)
                }
            }
        }, "androidInterface")
    }

    private data class ButtonConfig(
        val text: String,
        val onClick: View.OnClickListener
    )

    // 为每个WebView保存独立的控制面板
    private var aiControlPanels = mutableMapOf<WebView, LinearLayout>()
    private var normalEngineContainers = mutableMapOf<WebView, LinearLayout>()
    private var isAIEngineActiveMap = mutableMapOf<WebView, Boolean>()
    private var lastScrollY = 0
    private val SCROLL_THRESHOLD = 1000 // 滑动阈值

    private fun restoreSearchEnginePanel(webView: WebView, container: LinearLayout, isAI: Boolean) {
        // 清空当前容器
        container.removeAllViews()
        
        // 获取当前引擎
        val currentEngine = when (webView) {
            firstWebView -> leftEngineKey
            secondWebView -> centerEngineKey
            thirdWebView -> rightEngineKey
            else -> "baidu"
        }
        
        // 重新添加搜索引擎图标
        val engines = if (isAI) {
            listOf(
                SearchEngine("ChatGPT", "https://chat.openai.com/", R.drawable.ic_search, "ChatGPT"),
                SearchEngine("Claude", "https://claude.ai/", R.drawable.ic_baidu, "Claude AI助手"),
                SearchEngine("文心一言", "https://yiyan.baidu.com/", R.drawable.ic_baidu, "文心一言"),
                SearchEngine("通义千问", "https://qianwen.aliyun.com/", R.drawable.ic_google, "阿里通义千问"),
                SearchEngine("讯飞星火", "https://xinghuo.xfyun.cn/", R.drawable.ic_sogou, "讯飞星火")
            )
        } else {
            listOf(
                SearchEngine("百度", "https://www.baidu.com/s?wd={query}", R.drawable.ic_baidu, "百度搜索"),
                SearchEngine("谷歌", "https://www.google.com/search?q={query}", R.drawable.ic_google, "谷歌搜索"),
                SearchEngine("必应", "https://www.bing.com/search?q={query}", R.drawable.ic_bing, "必应搜索"),
                SearchEngine("搜狗", "https://www.sogou.com/web?query={query}", R.drawable.ic_sogou, "搜狗搜索"),
                SearchEngine("360", "https://www.so.com/s?q={query}", R.drawable.ic_360, "360搜索")
            )
        }
        
        addSearchEngineIcons(container, engines, webView, currentEngine, isAI)
    }

        private data class ButtonInfo(val text: String, val iconRes: Int, val onClick: () -> Unit)

    private fun createControlButton(text: String, iconRes: Int, onClick: () -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = 8.dpToPx(this@DualFloatingWebViewService)
                marginEnd = 8.dpToPx(this@DualFloatingWebViewService)
            }
            gravity = Gravity.CENTER

            // 添加图标
            addView(ImageView(context).apply {
                setImageResource(iconRes)
                layoutParams = LinearLayout.LayoutParams(
                    32.dpToPx(this@DualFloatingWebViewService),
                    32.dpToPx(this@DualFloatingWebViewService)
                )
                setOnClickListener { onClick() }
            })

            // 添加文字
            addView(TextView(context).apply {
                this.text = text
                textSize = 12f
                setTextColor(Color.BLACK)
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            })
        }
    }

    private fun createAIControlPanel(webView: WebView): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                12.dpToPx(this@DualFloatingWebViewService),
                4.dpToPx(this@DualFloatingWebViewService),
                12.dpToPx(this@DualFloatingWebViewService),
                4.dpToPx(this@DualFloatingWebViewService)
            )
            
            // 判断是否是AI模式
            val isAI = when(webView) {
                firstWebView -> leftEngineKey.startsWith("ai_")
                secondWebView -> centerEngineKey.startsWith("ai_") 
                thirdWebView -> rightEngineKey.startsWith("ai_")
                else -> false
            }

            // 创建主要功能按钮 - 根据是否AI搜索引擎提供不同功能
            val mainButtons = if (isAI) {
                listOf(
                    ButtonInfo("粘贴发送", R.drawable.ic_paste) {
                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.primaryClip?.getItemAt(0)?.text?.toString()?.let { text ->
                            // 使用更可靠的方式粘贴并发送
                            autoFillAndSendClipboardContent(webView)
                            
                            // 显示提示
                            Toast.makeText(this@DualFloatingWebViewService, "已发送内容", Toast.LENGTH_SHORT).show()
                        }
                    },
                    ButtonInfo("清空", R.drawable.ic_close) {
                        // 清空输入框
                        webView.evaluateJavascript("""
                            (function() {
                                var textareas = document.querySelectorAll('textarea');
                                for(var i = 0; i < textareas.length; i++) {
                                    textareas[i].value = '';
                                }
                                return true;
                            })()
                        """.trimIndent(), null)
                        
                        Toast.makeText(this@DualFloatingWebViewService, "已清空输入", Toast.LENGTH_SHORT).show()
                    },
                    ButtonInfo("重新生成", R.drawable.ic_refresh) {
                        // 查找并点击"重新生成"按钮
                        webView.evaluateJavascript("""
                            (function() {
                                var buttons = document.querySelectorAll('button');
                                for(var i = 0; i < buttons.length; i++) {
                                    if(buttons[i].textContent.includes('重新') || 
                                       buttons[i].textContent.includes('Regenerate') || 
                                       buttons[i].textContent.includes('retry') ||
                                       buttons[i].textContent.includes('重试')) {
                                        buttons[i].click();
                                        return true;
                                    }
                                }
                                return false;
                            })()
                        """.trimIndent()) { result ->
                            if (result == "true") {
                                Toast.makeText(this@DualFloatingWebViewService, "已发送重新生成请求", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this@DualFloatingWebViewService, "未找到重新生成按钮", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    ButtonInfo("复制", R.drawable.ic_content_copy) {
                        // 复制最后一条消息
                        webView.evaluateJavascript("""
                            (function() {
                                // 尝试获取最后一个消息元素
                                var messages = document.querySelectorAll('[role="region"]');
                                if (messages.length > 0) {
                                    var lastMessage = messages[messages.length - 1];
                                    var text = lastMessage.innerText;
                                    return text;
                                }
                                
                                // 备用方法
                                var elements = document.querySelectorAll('p');
                                if (elements.length > 0) {
                                    var lastElement = elements[elements.length - 1];
                                    return lastElement.innerText;
                                }
                                return "";
                            })()
                        """.trimIndent()) { result ->
                            val text = result?.trim('"')?.replace("\\n", "\n") ?: ""
                            if (text.isNotEmpty()) {
                                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("AI回复", text)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(this@DualFloatingWebViewService, "已复制回复内容", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this@DualFloatingWebViewService, "未找到可复制内容", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    ButtonInfo("停止生成", R.drawable.ic_refresh) {
                        // 查找并点击"停止生成"按钮
                        webView.evaluateJavascript("""
                            (function() {
                                var buttons = document.querySelectorAll('button');
                                for(var i = 0; i < buttons.length; i++) {
                                    if(buttons[i].textContent.includes('停止') || 
                                       buttons[i].textContent.includes('Stop') || 
                                       buttons[i].getAttribute('aria-label')?.includes('Stop')) {
                                        buttons[i].click();
                                        return true;
                                    }
                                }
                                return false;
                            })()
                        """.trimIndent()) { result ->
                            if (result == "true") {
                                Toast.makeText(this@DualFloatingWebViewService, "已停止生成", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this@DualFloatingWebViewService, "未找到停止按钮", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
            } else {
                listOf(
                    ButtonInfo("粘贴", R.drawable.ic_paste) {
                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.primaryClip?.getItemAt(0)?.text?.toString()?.let { text ->
                            // 粘贴到当前选中的输入框
                            webView.evaluateJavascript("""
                                (function() {
                                    var activeElement = document.activeElement;
                                    if (activeElement && (activeElement.tagName === 'INPUT' || activeElement.tagName === 'TEXTAREA')) {
                                        activeElement.value = '$text';
                                        return true;
                                    }
                                    return false;
                                })()
                            """.trimIndent()) { result ->
                                if (result == "true") {
                                    Toast.makeText(this@DualFloatingWebViewService, "已粘贴内容", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(this@DualFloatingWebViewService, "未找到输入框", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    ButtonInfo("复制", R.drawable.ic_content_copy) {
                        // 复制选中内容
                        webView.evaluateJavascript("""
                            (function() {
                                var selectedText = window.getSelection().toString();
                                return selectedText;
                            })()
                        """.trimIndent()) { result ->
                            val text = result?.trim('"') ?: ""
                            if (text.isNotEmpty()) {
                                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("选中内容", text)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(this@DualFloatingWebViewService, "已复制选中内容", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this@DualFloatingWebViewService, "未选中任何内容", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    ButtonInfo("返回", R.drawable.ic_back) {
                        if (webView.canGoBack()) webView.goBack()
                    },
                    ButtonInfo("前进", R.drawable.ic_forward) {
                        if (webView.canGoForward()) webView.goForward()
                    },
                    ButtonInfo("刷新", R.drawable.ic_refresh) {
                        webView.reload()
                    }
                )
            }

            // 添加主要功能按钮
            mainButtons.forEach { button ->
                addView(createControlButton(button.text, button.iconRes, button.onClick))
            }

            // 添加分隔线
            addView(View(context).apply {
                layoutParams = LinearLayout.LayoutParams(1.dpToPx(context), 24.dpToPx(context))
                setBackgroundColor(Color.LTGRAY)
                val margin = 8.dpToPx(context)
                (layoutParams as LinearLayout.LayoutParams).setMargins(margin, 0, margin, 0)
            })

            // 添加切换和关闭按钮
            val controlButtons = listOf(
                ButtonInfo("切换", R.drawable.ic_switch) {
                    // 获取对应的引擎容器
                    val container = when(webView) {
                        firstWebView -> firstEngineContainer
                        secondWebView -> secondEngineContainer
                        thirdWebView -> thirdEngineContainer
                        else -> null
                    }
                    val aiContainer = when(webView) {
                        firstWebView -> firstAIEngineContainer
                        secondWebView -> secondAIEngineContainer
                        thirdWebView -> thirdAIEngineContainer
                        else -> null
                    }
                    val aiScrollContainer = when(webView) {
                        firstWebView -> firstAIScrollContainer
                        secondWebView -> secondAIScrollContainer
                        thirdWebView -> thirdAIScrollContainer
                        else -> null
                    }
                    
                    // 切换搜索引擎类型
                    if (container != null && aiContainer != null && aiScrollContainer != null) {
                        // 清空当前面板
                        removeAllViews()
                        
                        // 判断当前引擎类型
                        val isNormalEngineShowing = container.visibility == View.VISIBLE
                        
                        if (isNormalEngineShowing) {
                            // 切换到AI引擎
                            container.visibility = View.GONE
                            aiScrollContainer.visibility = View.VISIBLE
                        } else {
                            // 切换到普通引擎
                            container.visibility = View.VISIBLE
                            aiScrollContainer.visibility = View.GONE
                        }
                        
                        // 重建按钮面板
                        closeButtonPanel(container)
                    }
                },
                ButtonInfo("关闭", R.drawable.ic_close) {
                    // 获取当前容器
                    val parentView = parent as? ViewGroup
                    if (parentView != null) {
                        // 判断父容器是否为AI引擎容器
                        val currentContainer = when {
                            webView == firstWebView && parentView.parent == firstAIScrollContainer -> firstAIEngineContainer
                            webView == secondWebView && parentView.parent == secondAIScrollContainer -> secondAIEngineContainer
                            webView == thirdWebView && parentView.parent == thirdAIScrollContainer -> thirdAIEngineContainer
                            webView == firstWebView -> firstEngineContainer
                            webView == secondWebView -> secondEngineContainer
                            webView == thirdWebView -> thirdEngineContainer
                            else -> null
                        }
                        
                        // 恢复适当的容器
                        if (currentContainer != null) {
                            // 清空当前视图
                            parentView.removeAllViews()
                            
                            // 恢复引擎图标
                            val isAI = when(webView) {
                                firstWebView -> leftEngineKey.startsWith("ai_")
                                secondWebView -> centerEngineKey.startsWith("ai_") 
                                thirdWebView -> rightEngineKey.startsWith("ai_")
                                else -> false
                            }
                            
                            if (isAI) {
                                // 重新创建AI引擎按钮
                                when (webView) {
                                    firstWebView -> createAIEngineButtons(webView, firstAIEngineContainer!!, "left") { key ->
                                        leftEngineKey = key
                                        settingsManager.setLeftWindowSearchEngine(key)
                                    }
                                    secondWebView -> createAIEngineButtons(webView, secondAIEngineContainer!!, "center") { key ->
                                        centerEngineKey = key
                                        settingsManager.setCenterWindowSearchEngine(key)
                                    }
                                    thirdWebView -> createAIEngineButtons(webView, thirdAIEngineContainer!!, "right") { key ->
                                        rightEngineKey = key
                                        settingsManager.setRightWindowSearchEngine(key)
                                    }
                                }
                            } else {
                                // 恢复普通搜索引擎面板
                                val standardContainer = when(webView) {
                                    firstWebView -> firstEngineContainer
                                    secondWebView -> secondEngineContainer
                                    thirdWebView -> thirdEngineContainer
                                    else -> null
                                }
                                if (standardContainer != null) {
                                    restoreSearchEnginePanel(webView, standardContainer, false)
                                }
                            }
                            
                            Toast.makeText(this@DualFloatingWebViewService, "已关闭控制面板", Toast.LENGTH_SHORT).show()
                        } else {
                            hideControlPanel()
                        }
                    } else {
                        hideControlPanel()
                    }
                }
            )

            // 添加控制按钮
            controlButtons.forEach { button ->
                addView(createControlButton(button.text, button.iconRes, button.onClick))
            }
        }
    }

    private fun hideControlPanel() {
        floatingView?.findViewById<View>(R.id.control_panel_container)?.visibility = View.GONE
    }

    private fun handleEngineToggle(webView: WebView) {
        isAIMode = !isAIMode
        updateEngineVisibility()
    }

    private fun updateEngineVisibility() {
        val normalContainer = floatingView?.findViewById<View>(R.id.normal_engine_container)
        val aiContainer = floatingView?.findViewById<View>(R.id.ai_engine_container)
        
        normalContainer?.visibility = if (isAIMode) View.GONE else View.VISIBLE
        aiContainer?.visibility = if (isAIMode) View.VISIBLE else View.GONE
    }

    private fun setupWebViewScrollListener(webView: WebView) {
        webView.viewTreeObserver.addOnScrollChangedListener {
            val scrollY = webView.scrollY
            if (Math.abs(scrollY - lastScrollY) > SCROLL_THRESHOLD) {
                // 向上滑动超过阈值，隐藏控制面板
                aiControlPanels[webView]?.visibility = View.GONE
            } else if (scrollY < SCROLL_THRESHOLD) {
                // 回到顶部附近，显示控制面板
                aiControlPanels[webView]?.visibility = View.VISIBLE
            }
            lastScrollY = scrollY
        }
    }

    private fun switchToAIMode(webView: WebView) {
        try {
            // 保存当前的搜索引擎容器引用
            val normalEngineContainer = when(webView) {
                firstWebView -> firstEngineContainer
                secondWebView -> secondEngineContainer
                thirdWebView -> thirdEngineContainer
                else -> null
            }
            
            normalEngineContainers[webView] = normalEngineContainer!!
            
            // 隐藏普通搜索引擎容器
            normalEngineContainer?.visibility = View.GONE
            
            // 创建并显示AI控制面板
            if (!aiControlPanels.containsKey(webView)) {
                val newPanel = createAIControlPanel(webView)
                aiControlPanels[webView] = newPanel
                // 将AI控制面板添加到布局中
                normalEngineContainer?.parent?.let { parent ->
                    if (parent is ViewGroup) {
                        val index = parent.indexOfChild(normalEngineContainer)
                        parent.addView(newPanel, index)
                    }
                }
            }
            
            // 显示当前WebView对应的控制面板
            aiControlPanels[webView]?.visibility = View.VISIBLE
            
            // 设置当前WebView的AI模式状态
            isAIEngineActiveMap[webView] = true
            
            // 设置滚动监听
            setupWebViewScrollListener(webView)
            
        } catch (e: Exception) {
            Log.e(TAG, "切换到AI模式失败", e)
        }
    }

    private fun switchToNormalMode(webView: WebView) {
        try {
            // 隐藏AI控制面板
            aiControlPanels[webView]?.visibility = View.GONE
            // 显示普通搜索引擎容器
            normalEngineContainers[webView]?.visibility = View.VISIBLE
            // 更新AI模式状态
            isAIEngineActiveMap[webView] = false
        } catch (e: Exception) {
            Log.e(TAG, "切换回普通模式失败", e)
        }
    }

    private fun switchToAIEngine(webView: WebView, engineContainer: LinearLayout, aiScrollContainer: HorizontalScrollView?, toggleButton: ImageView) {
        try {
            val isAIEngineShowing = engineContainer.visibility == View.GONE
            
            if (!isAIEngineShowing) {
                // 切换到AI引擎
                engineContainer.visibility = View.GONE
                aiScrollContainer?.visibility = View.VISIBLE
                
                // 创建并显示AI控制面板
                if (!aiControlPanels.containsKey(webView)) {
                    val newPanel = createAIControlPanel(webView)
                    aiControlPanels[webView] = newPanel
                }
                
                // 将AI控制面板添加到布局中
                aiScrollContainer?.let { container ->
                    // 移除所有现有的视图
                    container.removeAllViews()
                    // 添加控制面板
                    container.addView(aiControlPanels[webView])
                }
                
                toggleButton.setImageResource(R.drawable.ic_search)
                
                // 记录当前WebView的滚动位置
                lastScrollY = webView.scrollY
                
                // 设置滚动监听
                webView.setOnScrollChangeListener { _, _, scrollY, _, _ ->
                    if (scrollY > lastScrollY + SCROLL_THRESHOLD) {
                        // 向上滚动超过阈值，隐藏按钮面板
                        aiControlPanels[webView]?.visibility = View.GONE
                    } else if (scrollY < lastScrollY - SCROLL_THRESHOLD) {
                        // 向下滚动超过阈值，显示按钮面板
                        aiControlPanels[webView]?.visibility = View.VISIBLE
                    }
                    lastScrollY = scrollY
                }
            } else {
                // 切换回普通引擎
                engineContainer.visibility = View.VISIBLE
                aiScrollContainer?.visibility = View.GONE
                toggleButton.setImageResource(R.drawable.ic_ai_search)
                
                // 移除滚动监听
                webView.setOnScrollChangeListener(null)
            }
            
            // 使用Handler.post确保提示在UI更新后显示
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(
                    this,
                    if (isAIEngineShowing) "已切换到普通搜索" else "已切换到AI搜索",
                    Toast.LENGTH_SHORT
                ).show()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "切换搜索引擎失败: ${e.message}")
        }
    }

    @Synchronized
    private fun forceRestoreAllContainers() {
        try {
            // 恢复第一个容器
            firstWebView?.let { webView ->
                firstEngineContainer?.let { container ->
                    container.removeAllViews()
                    container.visibility = View.VISIBLE
                    restoreSearchEnginePanel(webView, container, leftEngineKey.startsWith("ai_"))
                    aiControlPanels[webView]?.visibility = View.GONE
                    isAIEngineActiveMap[webView] = false
                }
            }
            
            // 恢复第二个容器
            secondWebView?.let { webView ->
                secondEngineContainer?.let { container ->
                    container.removeAllViews()
                    container.visibility = View.VISIBLE
                    restoreSearchEnginePanel(webView, container, centerEngineKey.startsWith("ai_"))
                    aiControlPanels[webView]?.visibility = View.GONE
                    isAIEngineActiveMap[webView] = false
                }
            }
            
            // 恢复第三个容器
            thirdWebView?.let { webView ->
                thirdEngineContainer?.let { container ->
                    container.removeAllViews()
                    container.visibility = View.VISIBLE
                    restoreSearchEnginePanel(webView, container, rightEngineKey.startsWith("ai_"))
                    aiControlPanels[webView]?.visibility = View.GONE
                    isAIEngineActiveMap[webView] = false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "强制恢复所有容器失败", e)
            throw e
        }
    }

    @Synchronized
    private fun closeButtonPanel(container: LinearLayout?): Boolean {
        try {
            if (container == null) return false
            
            // 获取对应的WebView
            val webView = when (container) {
                firstEngineContainer, firstAIEngineContainer -> firstWebView
                secondEngineContainer, secondAIEngineContainer -> secondWebView
                thirdEngineContainer, thirdAIEngineContainer -> thirdWebView
                else -> null
            }
            
            if (webView != null) {
                // 只隐藏AI控制面板
                aiControlPanels[webView]?.visibility = View.GONE
                
                // 更新AI状态
                isAIEngineActiveMap[webView] = false
                
                // 显示提示
                Toast.makeText(this, "已关闭控制面板", Toast.LENGTH_SHORT).show()
                
                return true
            }
            
            return false
        } catch (e: Exception) {
            Log.e(TAG, "关闭按钮面板失败", e)
            return false
        }
    }

    private fun setupEngineToggles() {
        // 不再需要设置切换按钮，因为现在两种搜索引擎都同时显示
        // 隐藏所有切换按钮
        firstEngineToggle?.visibility = View.GONE
        secondEngineToggle?.visibility = View.GONE
        thirdEngineToggle?.visibility = View.GONE
        
        // 确保所有AI搜索引擎按钮已创建
        firstWebView?.let { webView ->
            firstAIEngineContainer?.let { container ->
                if (container.childCount == 0) {
                    createAIEngineButtons(webView, container as LinearLayout, "left") { key ->
                        leftEngineKey = key
                        settingsManager.setLeftWindowSearchEngine(key)
                    }
                }
            }
        }
        
        secondWebView?.let { webView ->
            secondAIEngineContainer?.let { container ->
                if (container.childCount == 0) {
                    createAIEngineButtons(webView, container as LinearLayout, "center") { key ->
                        centerEngineKey = key
                        settingsManager.setCenterWindowSearchEngine(key)
                    }
                }
            }
        }
        
        thirdWebView?.let { webView ->
            thirdAIEngineContainer?.let { container ->
                if (container.childCount == 0) {
                    createAIEngineButtons(webView, container as LinearLayout, "right") { key ->
                        rightEngineKey = key
                        settingsManager.setRightWindowSearchEngine(key)
                    }
                }
            }
        }
        
        // 确保两类搜索引擎同时可见
        firstAIScrollContainer?.visibility = View.VISIBLE
        firstEngineContainer?.visibility = View.VISIBLE
        
        secondAIScrollContainer?.visibility = if (windowCount >= 2) View.VISIBLE else View.GONE
        secondEngineContainer?.visibility = if (windowCount >= 2) View.VISIBLE else View.GONE
        
        thirdAIScrollContainer?.visibility = if (windowCount >= 3) View.VISIBLE else View.GONE
        thirdEngineContainer?.visibility = if (windowCount >= 3) View.VISIBLE else View.GONE
    }

    private fun switchToButtonPanel(webView: WebView, container: LinearLayout) {
        // 记录面板状态切换
        Log.d("DualFloatingWebView", "切换到按钮面板 - 输入容器：$container")
        
        // 保存原始内容，以便后续恢复
        val originalChildren = mutableListOf<View>()
        for (i in 0 until container.childCount) {
            originalChildren.add(container.getChildAt(i))
        }
        
        // 保存恢复信息到容器的tag中
        container.tag = mapOf(
            "originalContent" to true,
            "webView" to webView
        )
        
        // 清空当前容器
        container.removeAllViews()
        
        // 创建按钮面板
        val buttonPanel = createAIControlPanel(webView)
        
        // 设置面板样式
        buttonPanel.apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(8.dpToPx(this@DualFloatingWebViewService),
                      4.dpToPx(this@DualFloatingWebViewService),
                      8.dpToPx(this@DualFloatingWebViewService),
                      4.dpToPx(this@DualFloatingWebViewService))
                      
            // 给按钮面板添加标记，用于恢复时识别
            tag = "buttonPanel"
        }
        
        // 添加按钮面板到容器
        container.addView(buttonPanel)
        
        // 添加震动反馈
        try {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(30)
            }
        } catch (e: Exception) {
            Log.e(TAG, "震动反馈失败", e)
        }
    }

    private var isAIMode = false

    private fun setupSingleEngineToggle(
        toggleButton: ImageView?,
        webView: WebView?,
        engineContainer: ViewGroup?,
        aiContainer: ViewGroup?,
        aiScrollContainer: View?,
        position: String,
        saveEngineKey: (String) -> Unit
    ) {
        toggleButton?.setOnClickListener {
            val webViewNotNull = webView ?: return@setOnClickListener
            val engineContainerNotNull = engineContainer ?: return@setOnClickListener
            val aiContainerNotNull = aiContainer ?: return@setOnClickListener
            val aiScrollContainerNotNull = aiScrollContainer ?: return@setOnClickListener
            
            val data = EngineToggleData(
                webView = webViewNotNull,
                engineContainer = engineContainerNotNull,
                aiContainer = aiContainerNotNull,
                aiScrollContainer = aiScrollContainerNotNull,
                position = position,
                saveEngineKey = saveEngineKey
            )
            
            handleEngineToggle(
                data = data,
                toggleButton = it as ImageView,
                isShowingNormal = engineContainerNotNull.visibility == View.VISIBLE
            )
        }
    }

    private data class EngineToggleData(
        val webView: WebView,
        val engineContainer: ViewGroup,
        val aiContainer: ViewGroup,
        val aiScrollContainer: View,
        val position: String,
        val saveEngineKey: (String) -> Unit
    )

    private fun handleEngineToggle(
        data: EngineToggleData,
        toggleButton: ImageView,
        isShowingNormal: Boolean
    ) {
        if (isShowingNormal) {
            // 使用统一的动画助手函数
            switchEngineWithAnimation(
                data.engineContainer,
                data.aiContainer,
                data.aiScrollContainer,
                toggleButton,
                true,
                data.webView,
                data.position,
                data.saveEngineKey
            )
                    } else {
            // 切换回普通模式
            switchEngineWithAnimation(
                data.engineContainer,
                data.aiContainer,
                data.aiScrollContainer,
                toggleButton,
                false,
                data.webView,
                data.position,
                { _ -> }
            )
        }
        
        // 保存当前状态
        settingsManager.setIsAIMode(!isShowingNormal)
    }

    private fun switchEngineWithAnimation(
        normalContainer: View,
        aiContainer: ViewGroup,
        aiScrollContainer: View,
        toggleButton: ImageView,
        toAIMode: Boolean,
        webView: WebView,
        position: String,
        saveEngineFunction: (String) -> Unit
    ) {
        // 确保两个容器都存在
        if (normalContainer == null || aiContainer == null) {
            Log.e(TAG, "容器未正确初始化")
            return
        }
        
        // 切换前先检查目标容器是否已经创建了图标
        if (toAIMode && aiContainer.childCount == 0) {
            createAIEngineButtons(webView, aiContainer as LinearLayout, position, saveEngineFunction)
        }
        
        // 执行切换动画
        if (toAIMode) {
            normalContainer.animate()
                .alpha(0f)
                .withEndAction {
                    normalContainer.visibility = View.GONE
                    aiContainer.visibility = View.VISIBLE
                    aiContainer.alpha = 0f
                    aiContainer.animate().alpha(1f).start()
                }.start()
        } else {
            aiContainer.animate()
                .alpha(0f)
                .withEndAction {
                    aiContainer.visibility = View.GONE
                    normalContainer.visibility = View.VISIBLE
                    normalContainer.alpha = 0f
                    normalContainer.animate().alpha(1f).start()
                }.start()
        }
    }

    /**
     * 从Google Favicon服务加载网站图标
     */
    private fun getFaviconUrl(url: String): String {
        // 提取域名
        val domain = try {
            val uri = Uri.parse(url)
            uri.host ?: url
        } catch (e: Exception) {
            url
        }
        
        // 返回Google Favicon服务URL
        return "https://www.google.com/s2/favicons?domain=$domain&sz=64"
    }

    /**
     * 加载网站Favicon并设置给ImageView
     */
    private fun loadFavicon(url: String, imageView: ImageView, defaultIconRes: Int) {
        try {
            // 先设置默认图标
            imageView.setImageResource(defaultIconRes)
            
            // 在后台线程加载Favicon
            Thread {
                try {
                    val faviconUrl = getFaviconUrl(url)
                    val connection = URL(faviconUrl).openConnection() as HttpURLConnection
                    connection.connectTimeout = 5000
                    
                    val bitmap = BitmapFactory.decodeStream(connection.inputStream)
                    
                    // 在主线程更新UI
                    Handler(Looper.getMainLooper()).post {
                        if (bitmap != null) {
                            imageView.setImageBitmap(bitmap)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("DualFloatingWebView", "加载Favicon失败: ${e.message}")
                }
            }.start()
        } catch (e: Exception) {
            Log.e("DualFloatingWebView", "设置Favicon失败: ${e.message}")
        }
    }

    private fun createAIEngineButtons(webView: WebView, aiContainer: LinearLayout, engineKeyPrefix: String, saveEngineFunction: (String) -> Unit) {
        // 清空容器
        aiContainer.removeAllViews()
        
        // 创建控制面板容器
        val controlPanelContainer = LinearLayout(this).apply {
            id = View.generateViewId()
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            visibility = View.GONE
        }
        aiContainer.addView(controlPanelContainer)
        
        // 创建控制面板
        val controlPanel = createAIControlPanel(webView)
        controlPanelContainer.addView(controlPanel)
        
        // 创建水平布局
        val buttonLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                12.dpToPx(this@DualFloatingWebViewService),
                4.dpToPx(this@DualFloatingWebViewService),
                12.dpToPx(this@DualFloatingWebViewService),
                4.dpToPx(this@DualFloatingWebViewService)
            )
        }
        
        // AI搜索引擎列表，使用特定的默认图标
        val aiEngines = listOf(
            Triple("DeepSeek对话", "chat://deepseek", R.drawable.ic_search),
            Triple("ChatGPT对话", "chat://chatgpt", R.drawable.ic_search),
            Triple("ChatGPT", "https://chat.openai.com/", R.drawable.ic_search),
            Triple("Claude", "https://claude.ai/", R.drawable.ic_search),
            Triple("文心一言", "https://yiyan.baidu.com/", R.drawable.ic_search),
            Triple("通义千问", "https://qianwen.aliyun.com/", R.drawable.ic_search),
            Triple("讯飞星火", "https://xinghuo.xfyun.cn/", R.drawable.ic_search)
        )
        
        // 为每个AI引擎创建按钮
        aiEngines.forEach { (name, url, defaultIconRes) ->
            // 创建图标
            val iconView = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    56.dpToPx(this@DualFloatingWebViewService),
                    56.dpToPx(this@DualFloatingWebViewService)
                ).apply {
                    marginStart = 8.dpToPx(this@DualFloatingWebViewService)
                    marginEnd = 8.dpToPx(this@DualFloatingWebViewService)
                }
                
                // 设置圆形背景
                background = ContextCompat.getDrawable(this@DualFloatingWebViewService, R.drawable.circle_button_background)
                // 设置四边的内边距
                setPadding(
                    8.dpToPx(this@DualFloatingWebViewService),
                    8.dpToPx(this@DualFloatingWebViewService),
                    8.dpToPx(this@DualFloatingWebViewService),
                    8.dpToPx(this@DualFloatingWebViewService)
                )
                
                // 加载Favicon
                loadFavicon(url, this, defaultIconRes)
                
                // 设置点击事件
                isClickable = true
                isFocusable = true
                setOnLongClickListener {
                    // 显示控制面板
                    val container = parent?.parent as? ViewGroup
                    container?.findViewById<View>(controlPanelContainer.id)?.visibility = View.VISIBLE
                    true
                }
                            setOnClickListener {
                // 显示提示信息
                Toast.makeText(this@DualFloatingWebViewService, "正在打开: $name", Toast.LENGTH_SHORT).show()
                
                // 根据URL判断是否为聊天模式
                if (url.startsWith("chat://")) {
                    handleAIEngineSelection(name)
                } else {
                    // 加载URL
                    webView.loadUrl(url)
                    // 切换到普通模式
                    isChatMode = false
                    switchToNormalMode()
                }
                
                // 保存引擎键值
                saveEngineFunction("ai_${name.lowercase()}")
                
                // 切换到文字按钮面板
                switchToButtonPanel(webView, aiContainer)
                
                // 添加震动反馈
                try {
                    val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(30)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "震动反馈失败", e)
                }
            }
                
                // 设置内容描述，便于无障碍访问
                contentDescription = name
            }
            
            // 直接添加图标到布局
            buttonLayout.addView(iconView)
        }
        
        // 添加整个布局到容器
        aiContainer.addView(buttonLayout)
        
        // 记录日志
        Log.d("DualFloatingWebView", "已添加${aiEngines.size}个AI搜索引擎按钮到容器")
    }

    private lateinit var chatManager: ChatManager
    private var isChatMode = false
    private var currentChatView: View? = null
    private var messageInput: EditText? = null
    private var sendButton: ImageButton? = null
    private var isDeepSeekChat = true  // true为DeepSeek，false为ChatGPT

    private fun initializeFirstWebView() {
        val inflater = LayoutInflater.from(this)
        
        // 创建聊天视图
        currentChatView = inflater.inflate(R.layout.chat_webview_layout, null)
        firstWebView = currentChatView?.findViewById(R.id.chatWebView)
        messageInput = currentChatView?.findViewById(R.id.messageInput)
        sendButton = currentChatView?.findViewById(R.id.sendButton)
        
        // 初始化ChatManager
        chatManager = ChatManager(this)
        
        // 设置发送按钮点击事件
        sendButton?.setOnClickListener {
            val message = messageInput?.text?.toString()?.trim() ?: ""
            if (message.isNotEmpty()) {
                try {
                    chatManager.sendMessage(message, firstWebView!!, isDeepSeekChat)
                    messageInput?.text?.clear()
                } catch (e: IllegalStateException) {
                    // 创建输入框布局
                    val inputLayout = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(50, 30, 50, 30)
                    }

                    val apiKeyInput = EditText(this).apply {
                        hint = if (isDeepSeekChat) "请输入 DeepSeek API 密钥" else "请输入 ChatGPT API 密钥"
                        inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                        setSingleLine()
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                    }

                    inputLayout.addView(apiKeyInput)

                    // 创建一个AlertDialog来显示错误信息和API密钥输入框
                    val builder = android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
                        .setTitle("设置API密钥")
                        .setView(inputLayout)
                        .setPositiveButton("确定") { dialog, _ ->
                            val apiKey = apiKeyInput.text.toString().trim()
                            if (apiKey.isNotEmpty()) {
                                if (isDeepSeekChat) {
                                    settingsManager.setDeepSeekApiKey(apiKey)
                                    Toast.makeText(this, "DeepSeek API密钥已保存", Toast.LENGTH_SHORT).show()
                                } else {
                                    settingsManager.setChatGPTApiKey(apiKey)
                                    Toast.makeText(this, "ChatGPT API密钥已保存", Toast.LENGTH_SHORT).show()
                                }
                                // 保存后自动重试发送消息
                                try {
                                    chatManager.sendMessage(message, firstWebView!!, isDeepSeekChat)
                                    messageInput?.text?.clear()
                                } catch (e: Exception) {
                                    Toast.makeText(this, "发送失败：${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                            dialog.dismiss()
                        }
                        .setNegativeButton("取消") { dialog, _ ->
                            dialog.dismiss()
                        }
                    
                    // 确保在系统窗口上显示对话框
                    builder.create().apply {
                        window?.setType(
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                            } else {
                                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
                            }
                        )
                        show()
                    }
                }
            }
        }
        
        // 设置输入框回车发送
        messageInput?.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEND ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {
                sendButton?.performClick()
                true
            } else {
                false
            }
        }
    }

    // 在AISearchEngine选择时切换聊天模式
    private fun handleAIEngineSelection(engineName: String) {
        when (engineName) {
            "DeepSeek对话" -> {
                isChatMode = true
                isDeepSeekChat = true
                switchToChatMode()
            }
            "ChatGPT对话" -> {
                isChatMode = true
                isDeepSeekChat = false
                switchToChatMode()
            }
            else -> {
                isChatMode = false
                switchToNormalMode()
            }
        }
    }

    private fun switchToChatMode() {
        // 如果还没有初始化聊天视图，先初始化
        if (currentChatView == null) {
            initializeFirstWebView()
        }

        // 移除当前的WebView
        container?.removeAllViews()
        
        // 添加聊天视图
        container?.addView(currentChatView)
        
        // 初始化聊天界面
        chatManager.initWebView(firstWebView!!)
        
        // 显示输入区域
        messageInput?.visibility = View.VISIBLE
        sendButton?.visibility = View.VISIBLE
        
        // 显示当前模式的提示
        val modeName = if (isDeepSeekChat) "DeepSeek" else "ChatGPT"
        Toast.makeText(this, "已切换到${modeName}对话模式", Toast.LENGTH_SHORT).show()
    }

    private fun switchToNormalMode() {
        // 移除聊天视图
        container?.removeAllViews()
        
        // 重新创建普通WebView
        firstWebView = WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            // 配置WebView设置
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                // 其他必要的WebView设置...
            }
        }
        
        // 添加普通WebView
        container?.addView(firstWebView)
        
        // 隐藏聊天相关控件
        messageInput?.visibility = View.GONE
        sendButton?.visibility = View.GONE
    }
}