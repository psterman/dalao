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
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Point
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
import java.net.URLEncoder
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.TimeUnit
import kotlin.concurrent.withLock
import com.example.aifloatingball.model.HandleType
import android.widget.PopupWindow
import android.view.ViewPropertyAnimator

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
    private var dualSearchButton: ImageButton? = null
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
    private var centerEngineKey: String = "bing"  // 添加中间引擎键
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

    override fun onCreate() {
        super.onCreate()
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
            
            // 设置WebView的文本选择功能
            enableTextSelectionOnWebViews()
            
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
                firstWebView?.loadUrl(url)
                
                // 根据URL确定搜索引擎
                val isGoogle = url.contains("google.com")
                val isBaidu = url.contains("baidu.com")
                
                // 如果是百度或谷歌，加载另一个搜索引擎
                if (windowCount >= 2) {
                    if (isGoogle) {
                        secondWebView?.loadUrl("https://www.baidu.com")
                    } else {
                        secondWebView?.loadUrl("https://www.google.com")
                    }
                }
                
                // 如果是三窗口模式，加载第三个窗口
                if (windowCount >= 3) {
                    if (isGoogle || isBaidu) {
                        thirdWebView?.loadUrl("https://www.bing.com")
                    } else {
                        thirdWebView?.loadUrl("https://www.zhihu.com")
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
            
            // 获取用户设置的默认窗口数量
            val windowCount = settingsManager.getDefaultWindowCount()
            
            // 根据窗口数量显示或隐藏相应的窗口
            updateWindowVisibility(windowCount)
            
        } catch (e: Exception) {
            Log.e(TAG, "创建悬浮窗失败", e)
            stopSelf()
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
        dualSearchButton = floatingView?.findViewById(R.id.btn_dual_search)
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
        saveButton = floatingView?.findViewById(R.id.btn_save_engines) // 添加保存按钮
        
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

    private fun setupWebViews() {
        val webViews = listOfNotNull(firstWebView, secondWebView, thirdWebView)
        
        webViews.forEach { webView ->
            webView.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
            }

            // 设置触摸事件监听
            webView.setOnTouchListener { view, event ->
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
                        
                        // 检查是否点击了输入框
                        webView.evaluateJavascript("""
                            (function() {
                                var x = ${event.x};
                                var y = ${event.y};
                                var element = document.elementFromPoint(x, y);
                                return element && (element.tagName === 'INPUT' || element.tagName === 'TEXTAREA');
                            })();
                        """.trimIndent()) { result ->
                            if (result == "true") {
                                // 如果是输入框，允许获取焦点
                                toggleWindowFocusableFlag(true)
                            }
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        val touchDuration = SystemClock.uptimeMillis() - lastTouchDownTime
                        val touchMoved = Math.abs(event.x - lastTouchX) > 10 || 
                                       Math.abs(event.y - lastTouchY) > 10
                        
                        if (!touchMoved) {
                            if (touchDuration < ViewConfiguration.getLongPressTimeout()) {
                                // 短按 - 尝试激活选择
                                activateSelection(webView, event)
                            } else {
                                // 长按 - 处理长按事件
                                handleLongPress(webView, event)
                            }
                        }
                    }
                }
                false // 继续传递事件给WebView
            }

            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    view?.let { 
                        enableTextSelectionMode(it)
                    }
                }
            }
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
                    val jsonStr = result.trim('"').replace("\\\\\"", "\"")
                    val json = JSONObject(jsonStr)
                    
                    val selectedText = json.getString("text")
                    if (selectedText.isNotEmpty()) {
                        currentSelectedText = selectedText
                        
                        mainHandler.post {
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
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "处理文本选择结果失败", e)
            }
        }
    }

    private fun clearTextSelection() {
        textSelectionManager?.clearSelection()
        textSelectionManager = null
        currentSelectionState = null
        hideTextSelectionMenu()
    }

    private fun showTextSelectionMenuSafely(webView: WebView, x: Int, y: Int) {
        if (textSelectionMenu?.isShowing == true) {
            textSelectionMenu?.dismiss()
        }

        val menuView = LayoutInflater.from(this).inflate(R.layout.text_selection_menu, null)
        textSelectionMenu = PopupWindow(
            menuView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            isOutsideTouchable = true
            isFocusable = true
            elevation = resources.getDimensionPixelSize(R.dimen.menu_elevation).toFloat()
        }

        menuView.findViewById<View>(R.id.menu_copy).setOnClickListener {
            copySelectedText(webView)
            clearTextSelection()
        }

        menuView.findViewById<View>(R.id.menu_share).setOnClickListener {
            shareSelectedText(webView)
            clearTextSelection()
        }

        // 计算菜单位置
        val location = IntArray(2)
        webView.getLocationOnScreen(location)
        val menuX = location[0] + x - (textSelectionMenu?.contentView?.measuredWidth ?: 0) / 2
        val menuY = location[1] + y - (textSelectionMenu?.contentView?.measuredHeight ?: 0) - 20

        textSelectionMenu?.showAtLocation(webView, Gravity.NO_GRAVITY, menuX, menuY)
    }

    private fun hideTextSelectionMenu() {
        textSelectionMenu?.dismiss()
        textSelectionMenu = null
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
        // 第一个窗口的切换按钮
        firstEngineToggle?.setOnClickListener {
            val isAIVisible = firstAIScrollContainer?.visibility == View.VISIBLE
            
            // 切换AI和普通搜索引擎视图
            firstAIScrollContainer?.visibility = if (isAIVisible) View.GONE else View.VISIBLE
            
            // 更新按钮图标
            firstEngineToggle?.setImageResource(
                if (isAIVisible) R.drawable.ic_search else R.drawable.ic_ai_search
            )
        }
        
        // 第二个窗口的切换按钮
        secondEngineToggle?.setOnClickListener {
            val isAIVisible = secondAIScrollContainer?.visibility == View.VISIBLE
            
            // 切换AI和普通搜索引擎视图
            secondAIScrollContainer?.visibility = if (isAIVisible) View.GONE else View.VISIBLE
            
            // 更新按钮图标
            secondEngineToggle?.setImageResource(
                if (isAIVisible) R.drawable.ic_search else R.drawable.ic_ai_search
            )
        }
        
        // 第三个窗口的切换按钮
        thirdEngineToggle?.setOnClickListener {
            val isAIVisible = thirdAIScrollContainer?.visibility == View.VISIBLE
            
            // 切换AI和普通搜索引擎视图
            thirdAIScrollContainer?.visibility = if (isAIVisible) View.GONE else View.VISIBLE
            
            // 更新按钮图标
            thirdEngineToggle?.setImageResource(
                if (isAIVisible) R.drawable.ic_search else R.drawable.ic_ai_search
            )
        }
    }

    /**
     * 设置引擎容器样式，提高显示效果
     */
    private fun setupEngineContainerStyle() {
        // 设置背景和边距
        val containers = listOf(
            firstEngineContainer, secondEngineContainer, thirdEngineContainer,
            firstAIEngineContainer, secondAIEngineContainer, thirdAIEngineContainer
        )
        
        // 确保引擎容器的高度合适
        containers.forEach { container ->
            container?.apply {
                // 使用半透明背景
                setBackgroundColor(Color.parseColor("#22000000"))
                setPadding(2.dpToPx(this@DualFloatingWebViewService), 
                           2.dpToPx(this@DualFloatingWebViewService), 
                           2.dpToPx(this@DualFloatingWebViewService), 
                           2.dpToPx(this@DualFloatingWebViewService))
                
                // 优化布局参数，确保内容不被截断
                // 不直接设置布局参数，而是保留原有的layoutParams类型
                val existingParams = layoutParams
                if (existingParams is LinearLayout.LayoutParams) {
                    existingParams.width = LinearLayout.LayoutParams.MATCH_PARENT
                    existingParams.height = LinearLayout.LayoutParams.WRAP_CONTENT
                }
            }
        }
        
        // 优化水平滚动视图，确保能够滚动查看所有图标
        val scrollContainers = listOf(
            firstAIScrollContainer,
            secondAIScrollContainer,
            thirdAIScrollContainer
        )
        
        scrollContainers.forEach { scrollView ->
            scrollView?.apply {
                // 设置滚动视图的样式
                setBackgroundColor(Color.parseColor("#11000000"))
                setPadding(1.dpToPx(this@DualFloatingWebViewService), 
                          1.dpToPx(this@DualFloatingWebViewService), 
                          1.dpToPx(this@DualFloatingWebViewService), 
                          1.dpToPx(this@DualFloatingWebViewService))
                
                // 显示水平滚动条，帮助用户理解可以滚动
                isHorizontalScrollBarEnabled = true
                
                // 根据父容器类型设置正确的LayoutParams
                val parent = parent
                if (parent != null) {
                    when (parent) {
                        is LinearLayout -> {
                            val params = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            )
                            layoutParams = params
                        }
                        is FrameLayout -> {
                            val params = FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.WRAP_CONTENT
                            )
                            layoutParams = params
                        }
                    }
                }
            }
        }
        
        // 单独调整AI容器的初始可见性
        firstAIScrollContainer?.visibility = View.GONE
        secondAIScrollContainer?.visibility = View.GONE
        thirdAIScrollContainer?.visibility = View.GONE
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

    private fun createSearchEngineSelector(searchBarLayout: LinearLayout, webView: WebView, isCenter: Boolean = false) {
        val horizontalScrollView = HorizontalScrollView(this).apply {
            // 确保使用正确的LayoutParams类型，因为我们添加到LinearLayout
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            // 显示滚动条以便用户了解可以滚动查看更多图标
            isHorizontalScrollBarEnabled = true
            
            // 使用背景资源或颜色而不是未定义的资源
            setBackgroundColor(android.graphics.Color.parseColor("#1A000000"))
        }

        val engineLayout = LinearLayout(this).apply {
            // 这里使用HorizontalScrollView的LayoutParams，因为它会被添加到HorizontalScrollView
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.HORIZONTAL
            setPadding(3.dpToPx(this@DualFloatingWebViewService), 
                      2.dpToPx(this@DualFloatingWebViewService), 
                      3.dpToPx(this@DualFloatingWebViewService), 
                      2.dpToPx(this@DualFloatingWebViewService))
            // 添加背景以增强视觉效果
            setBackgroundColor(Color.parseColor("#22000000"))
        }

        val engines = SearchEngine.DEFAULT_ENGINES

        for (engine in engines) {
            val engineIcon = createSelectorEngineIcon(engine.name)
            
            engineIcon.setOnClickListener {
                val currentEngine = engine
                val editText = searchBarLayout.findViewById<EditText>(R.id.search_edit_text)
                val searchText = editText.text.toString()
                
                // 保存选择的搜索引擎
                val windowPosition = if (isCenter) "center" else if (webView == firstWebView) "left" else "right"
                when (windowPosition) {
                    "left" -> settingsManager.setLeftWindowSearchEngine(currentEngine.name.lowercase())
                    "center" -> settingsManager.setCenterWindowSearchEngine(currentEngine.name.lowercase()) 
                    "right" -> settingsManager.setRightWindowSearchEngine(currentEngine.name.lowercase())
                }
                
                if (searchText.isNotEmpty()) {
                    performSearch(webView, searchText, currentEngine.url)
                }
                
                // 显示选中状态
                for (i in 0 until engineLayout.childCount) {
                    val view = engineLayout.getChildAt(i)
                    if (view is ImageView) {
                        view.setBackgroundResource(R.drawable.icon_background_selected)
                    }
                }
                engineIcon.setBackgroundResource(R.drawable.icon_background_selected)
            }
            
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
            
            engineLayout.addView(engineIcon)
        }

        horizontalScrollView.addView(engineLayout)
        
        // 设置水平滚动视图布局参数
        val hsvParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        hsvParams.setMargins(0, 2.dpToPx(this), 0, 2.dpToPx(this))
        
        // 将水平滚动视图添加到搜索栏布局
        searchBarLayout.addView(horizontalScrollView, hsvParams)
        
        // 将滚动位置设置为已选中的引擎
        horizontalScrollView.post {
            // 找到选中的引擎在engineLayout中的位置
            val selectedIndex = engines.indexOfFirst { 
                val windowPosition = if (isCenter) "center" else if (webView == firstWebView) "left" else "right"
                val defaultEngine = when (windowPosition) {
                    "left" -> settingsManager.getLeftWindowSearchEngine()
                    "center" -> settingsManager.getCenterWindowSearchEngine()
                    else -> settingsManager.getRightWindowSearchEngine()
                }
                it.name.lowercase() == defaultEngine
            }
            
            if (selectedIndex > 2) { // 只有当选择的引擎不在前几个时才滚动
                val childView = engineLayout.getChildAt(selectedIndex)
                horizontalScrollView.smoothScrollTo(childView.left - 30.dpToPx(this), 0)
            }
        }
    }

    private fun updateViewVisibilityByWindowCount() {
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
            }
        }
        
        // 更新布局
        container?.requestLayout()
    }

    // 根据窗口数量显示或隐藏窗口
    private fun updateWindowVisibility(windowCount: Int) {
        // 更新窗口数量变量
        this.windowCount = windowCount
        
        // 保存设置
        settingsManager.setDefaultWindowCount(windowCount)
        
        when (windowCount) {
            1 -> {
                // 只显示第一个窗口
                firstWebView?.visibility = View.VISIBLE
                divider1?.visibility = View.GONE
                secondWebView?.visibility = View.GONE
                divider2?.visibility = View.GONE
                thirdWebView?.visibility = View.GONE
            }
            2 -> {
                // 显示两个窗口
                firstWebView?.visibility = View.VISIBLE
                divider1?.visibility = View.VISIBLE
                secondWebView?.visibility = View.VISIBLE
                divider2?.visibility = View.GONE
                thirdWebView?.visibility = View.GONE
            }
            else -> {
                // 显示所有三个窗口
                firstWebView?.visibility = View.VISIBLE
                divider1?.visibility = View.VISIBLE
                secondWebView?.visibility = View.VISIBLE
                divider2?.visibility = View.VISIBLE
                thirdWebView?.visibility = View.VISIBLE
            }
        }
        
        // 更新窗口计数显示
        windowCountToggleView?.text = "$windowCount"
        
        // 更新布局参数
        updateLayoutParams()
    }

    private fun updateLayoutParams() {
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        
        val windowParams = floatingView?.layoutParams as? WindowManager.LayoutParams
        
        if (isHorizontalLayout) {
            windowParams?.width = WindowManager.LayoutParams.MATCH_PARENT
            windowParams?.height = (screenHeight * DEFAULT_HEIGHT_RATIO).toInt()
        } else {
            windowParams?.width = WindowManager.LayoutParams.MATCH_PARENT
            windowParams?.height = WindowManager.LayoutParams.MATCH_PARENT
        }
        
        try {
            windowManager.updateViewLayout(floatingView, windowParams)
        } catch (e: Exception) {
            Log.e(TAG, "更新窗口布局失败", e)
        }
        
        // 请求布局更新
        divider1?.requestLayout()
        divider2?.requestLayout()
        container?.requestLayout()
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
    }

    private fun saveCurrentSearchEngines() {
        try {
            val searchEngines = mutableListOf<SearchEngine>()
            
            // 收集所有活跃的WebView中的搜索引擎
            val activeWebViews = listOfNotNull(firstWebView, secondWebView, thirdWebView)
                .filter { it.visibility == View.VISIBLE }
            
            // 为每个WebView创建SearchEngine对象
            activeWebViews.forEachIndexed { index, webView ->
                val engineKey = when (index) {
                    0 -> leftEngineKey
                    1 -> centerEngineKey
                    2 -> rightEngineKey
                    else -> "baidu"
                }
                val isAI = engineKey.startsWith("ai_")
                val actualEngineKey = if (isAI) engineKey.substring(3) else engineKey
                
                // 获取当前URL和标题
                val url = webView.url ?: ""
                val title = webView.title ?: EngineUtil.getSearchEngineName(actualEngineKey, isAI)
                
                // 构建搜索引擎对象
                val engine = SearchEngine(
                    name = title,
                    url = convertToSearchUrl(url), // 转换为可用于搜索的URL
                    iconResId = R.drawable.ic_search,
                    description = "从浏览器保存的搜索引擎: ${EngineUtil.getSearchEngineName(actualEngineKey, isAI)}"
                )
                searchEngines.add(engine)
            }
            
            if (searchEngines.isNotEmpty()) {
                // 创建搜索引擎组合名称
                val groupName = searchEngines.joinToString(" + ") { 
                    it.name.take(10) // 截取名称，避免过长
                }
                
                // 保存搜索引擎组合
                val searchEngineManager = SearchEngineManager.getInstance(this)
                searchEngineManager.saveSearchEngineGroup(groupName, searchEngines)
                
                // 显示视觉反馈
                showSaveSuccessAnimation(saveButton)
                
                // 显示成功提示
                Toast.makeText(this, "已保存搜索引擎组合：$groupName", Toast.LENGTH_SHORT).show()
                
                // 通知 FloatingWindowService 更新快捷方式
                val intent = Intent("com.example.aifloatingball.ACTION_UPDATE_SHORTCUTS")
                sendBroadcast(intent)
                
                // 延迟一会确保广播接收器有时间处理
                Handler(Looper.getMainLooper()).postDelayed({
                    Log.d(TAG, "搜索引擎快捷方式已更新")
                }, 300)
            } else {
                Toast.makeText(this, "没有可保存的搜索引擎", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "保存搜索引擎组合失败", e)
            Toast.makeText(this, "保存失败：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    // 显示保存成功的动画效果
    private fun showSaveSuccessAnimation(view: View?) {
        view?.let {
            it.animate()
                .scaleX(1.2f)
                .scaleY(1.2f)
                .setDuration(150)
                .withEndAction {
                    it.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(150)
                        .start()
                }
                .start()
        }
    }
    
    // 将当前URL转换为可用于搜索的URL
    private fun convertToSearchUrl(url: String): String {
        try {
            // 尝试从URL中提取查询参数
            val uri = Uri.parse(url)
            
            // 常见搜索引擎查询参数名称
            val queryParamNames = listOf("q", "query", "word", "wd", "text", "search")
            
            // 尝试每一种可能的查询参数
            for (paramName in queryParamNames) {
                val queryValue = uri.getQueryParameter(paramName)
                if (!queryValue.isNullOrEmpty()) {
                    // 找到查询参数，替换为查询占位符
                    return url.replace(queryValue, "{query}")
                }
            }
            
            // 如果找不到查询参数，返回原始URL
            return url
        } catch (e: Exception) {
            Log.e(TAG, "转换搜索URL失败", e)
            return url
        }
    }

    /**
     * 切换到普通模式
     */
    private fun switchToNormalMode() {
        try {
            // 启动 HomeActivity
            val intent = Intent(this, HomeActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                
                // 如果当前有URL，传递给 HomeActivity
                when (windowCount) {
                    1 -> firstWebView?.url?.let { putExtra("url", it) }
                    2 -> {
                        // 获取第一个和第二个WebView的URL
                        val urls = mutableListOf<String>()
                        firstWebView?.url?.let { urls.add(it) }
                        secondWebView?.url?.let { urls.add(it) }
                        putExtra("urls", urls.toTypedArray())
                    }
                    else -> {
                        // 获取所有WebView的URL
                        val urls = mutableListOf<String>()
                        firstWebView?.url?.let { urls.add(it) }
                        secondWebView?.url?.let { urls.add(it) }
                        thirdWebView?.url?.let { urls.add(it) }
                        putExtra("urls", urls.toTypedArray())
                    }
                }
            }
            startActivity(intent)
            
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
     * 为所有WebView启用文本选择功能
     */
    private fun enableTextSelectionOnWebViews() {
        val webViews = listOfNotNull(firstWebView, secondWebView, thirdWebView)
        
        webViews.forEach { webView ->
            webView.isLongClickable = true
            
            // 设置长按监听器
            webView.setOnLongClickListener { view ->
                val event = view.tag as? MotionEvent ?: return@setOnLongClickListener false
                
                // 清除之前的选择
                textSelectionManager?.clearSelection()
                
                // 创建新的选择管理器，注意接口适配
                textSelectionManager = TextSelectionManager(
                    context = this@DualFloatingWebViewService,
                    webView = webView,
                    windowManager = windowManager,
                    onSelectionChanged = { selectedText: String ->
                        currentSelectedText = selectedText
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
                
                // 开始选择
                textSelectionManager?.startSelection(
                    event.x.toInt(),
                    event.y.toInt()
                )
                
                true
            }
            
            // 保存触摸事件
        }
    }

    /**
     * 切换窗口是否可获得焦点的标志，用于处理输入法
     * @param focusable 是否可获得焦点
     */
    private fun toggleWindowFocusableFlag(focusable: Boolean) {
        try {
            val params = floatingView?.layoutParams as? WindowManager.LayoutParams ?: return
            
            if (focusable) {
                // 移除FLAG_NOT_FOCUSABLE标志，使窗口可以获取焦点
                params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            } else {
                // 添加FLAG_NOT_FOCUSABLE标志，使窗口不可获取焦点
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            }
            
            // 应用新参数
            windowManager.updateViewLayout(floatingView, params)
            
            // 如果切换到可获取焦点，则请求焦点
            if (focusable) {
                floatingView?.requestFocus()
            }
        } catch (e: Exception) {
            Log.e(TAG, "切换窗口焦点状态失败: ${e.message}")
        }
    }

    private fun setupControls() {
        // 设置窗口切换按钮
        toggleLayoutButton?.setOnClickListener {
            isHorizontalLayout = !isHorizontalLayout
            updateLayoutOrientation()
        }

        // 设置搜索按钮
        dualSearchButton?.setOnClickListener {
            performDualSearch()
        }

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
            saveCurrentSearchEngines()
        }

        // 设置切换按钮
        switchToNormalButton?.setOnClickListener {
            switchToNormalMode()
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
        return when(engineKey.lowercase()) {
            "baidu" -> "https://www.baidu.com/s?wd=$query"
            "google" -> "https://www.google.com/search?q=$query"
            "bing" -> "https://www.bing.com/search?q=$query"
            "sogou" -> "https://www.sogou.com/web?query=$query"
            "360" -> "https://www.so.com/s?q=$query"
            "zhihu" -> "https://www.zhihu.com/search?q=$query"
            "bilibili" -> "https://search.bilibili.com/all?keyword=$query"
            "weibo" -> "https://s.weibo.com/weibo?q=$query"
            "douban" -> "https://www.douban.com/search?q=$query"
            "taobao" -> "https://s.taobao.com/search?q=$query"
            "jd" -> "https://search.jd.com/Search?keyword=$query"
            "douyin" -> "https://www.douyin.com/search/$query"
            "xiaohongshu" -> "https://www.xiaohongshu.com/search_result?keyword=$query"
            else -> "https://www.baidu.com/s?wd=$query"
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
} 