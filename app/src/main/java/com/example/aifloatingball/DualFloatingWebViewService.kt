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

            // 添加 JSBridge
            webView.addJavascriptInterface(WebViewJSBridge(webView), "NativeBridge")

            // 设置WebView的长按事件监听，用于处理链接长按
            webView.setOnLongClickListener { view ->
                val result = (view as WebView).hitTestResult
                
                when (result.type) {
                    WebView.HitTestResult.SRC_ANCHOR_TYPE,
                    WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE,
                    WebView.HitTestResult.IMAGE_TYPE -> {
                        // 获取长按位置的URL和链接文本
                        val url = result.extra
                        if (url != null) {
                            // 显示自定义链接菜单
                            showLinkActionMenu(webView, url, result.type)
                            return@setOnLongClickListener true
                        }
                    }
                }
                false
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
                        injectCustomMenuCode(it)
                        injectLinkContextMenuCode(it)
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
        Log.d(TAG, "初始化引擎切换按钮")
        
        // 第一个窗口的切换按钮
        firstEngineToggle?.setOnClickListener {
            try {
                Log.d(TAG, "第一个窗口切换按钮被点击")
                // 获取当前AI滚动容器可见性状态
                val isAIVisible = firstAIScrollContainer?.visibility == View.VISIBLE
                Log.d(TAG, "第一个窗口AI容器当前可见性: ${if (isAIVisible) "可见" else "不可见"}")
                
                // 直接切换容器可见性
                if (isAIVisible) {
                    // 从AI引擎切换到普通引擎
                    firstAIScrollContainer?.visibility = View.GONE
                    firstEngineToggle?.setImageResource(R.drawable.ic_ai_search)
                } else {
                    // 从普通引擎切换到AI引擎
                    firstAIScrollContainer?.visibility = View.VISIBLE
                    firstEngineToggle?.setImageResource(R.drawable.ic_search)
                }
                
                // 添加切换动画
                if (!isAIVisible) {
                    firstAIScrollContainer?.apply {
                        alpha = 0f
                        animate()
                            .alpha(1f)
                            .setDuration(200)
                            .start()
                    }
                }
                
                // 确认切换后的可见性状态
                Log.d(TAG, "切换后第一个窗口容器状态 - " +
                       "AI容器: ${if (firstAIScrollContainer?.visibility == View.VISIBLE) "可见" else "不可见"}")
                
                // 显示提示
                Toast.makeText(
                    this@DualFloatingWebViewService,
                    if (isAIVisible) "已切换到普通搜索" else "已切换到AI搜索",
                    Toast.LENGTH_SHORT
                ).show()
                
            } catch (e: Exception) {
                Log.e(TAG, "第一个窗口切换按钮出错", e)
                Toast.makeText(this, "切换引擎出错: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
        
        // 第二个窗口的切换按钮
        secondEngineToggle?.setOnClickListener {
            try {
                Log.d(TAG, "第二个窗口切换按钮被点击")
                // 获取当前AI容器可见性状态
                val isAIVisible = secondAIScrollContainer?.visibility == View.VISIBLE
                Log.d(TAG, "第二个窗口AI容器当前可见性: ${if (isAIVisible) "可见" else "不可见"}")
                
                // 直接切换容器可见性
                if (isAIVisible) {
                    // 从AI引擎切换到普通引擎
                    secondAIScrollContainer?.visibility = View.GONE
                    secondEngineToggle?.setImageResource(R.drawable.ic_ai_search)
                } else {
                    // 从普通引擎切换到AI引擎
                    secondAIScrollContainer?.visibility = View.VISIBLE
                    secondEngineToggle?.setImageResource(R.drawable.ic_search)
                }
                
                // 添加切换动画
                if (!isAIVisible) {
                    secondAIScrollContainer?.apply {
                        alpha = 0f
                        animate()
                            .alpha(1f)
                            .setDuration(200)
                            .start()
                    }
                }
                
                // 确认切换后的可见性状态
                Log.d(TAG, "切换后第二个窗口容器状态 - " +
                       "AI容器: ${if (secondAIScrollContainer?.visibility == View.VISIBLE) "可见" else "不可见"}")
                
                // 显示提示
                Toast.makeText(
                    this@DualFloatingWebViewService,
                    if (isAIVisible) "已切换到普通搜索" else "已切换到AI搜索",
                    Toast.LENGTH_SHORT
                ).show()
                
            } catch (e: Exception) {
                Log.e(TAG, "第二个窗口切换按钮出错", e)
                Toast.makeText(this, "切换引擎出错: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
        
        // 第三个窗口的切换按钮
        thirdEngineToggle?.setOnClickListener {
            try {
                Log.d(TAG, "第三个窗口切换按钮被点击")
                // 获取当前AI容器可见性状态
                val isAIVisible = thirdAIScrollContainer?.visibility == View.VISIBLE
                Log.d(TAG, "第三个窗口AI容器当前可见性: ${if (isAIVisible) "可见" else "不可见"}")
                
                // 直接切换容器可见性
                if (isAIVisible) {
                    // 从AI引擎切换到普通引擎
                    thirdAIScrollContainer?.visibility = View.GONE
                    thirdEngineToggle?.setImageResource(R.drawable.ic_ai_search)
                } else {
                    // 从普通引擎切换到AI引擎
                    thirdAIScrollContainer?.visibility = View.VISIBLE
                    thirdEngineToggle?.setImageResource(R.drawable.ic_search)
                }
                
                // 添加切换动画
                if (!isAIVisible) {
                    thirdAIScrollContainer?.apply {
                        alpha = 0f
                        animate()
                            .alpha(1f)
                            .setDuration(200)
                            .start()
                    }
                }
                
                // 确认切换后的可见性状态
                Log.d(TAG, "切换后第三个窗口容器状态 - " +
                       "AI容器: ${if (thirdAIScrollContainer?.visibility == View.VISIBLE) "可见" else "不可见"}")
                
                // 显示提示
                Toast.makeText(
                    this@DualFloatingWebViewService,
                    if (isAIVisible) "已切换到普通搜索" else "已切换到AI搜索",
                    Toast.LENGTH_SHORT
                ).show()
                
            } catch (e: Exception) {
                Log.e(TAG, "第三个窗口切换按钮出错", e)
                Toast.makeText(this, "切换引擎出错: ${e.message}", Toast.LENGTH_SHORT).show()
            }
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

        // 添加AI搜索引擎图标 - 确保无重复且使用不同图标区分
        val aiEngines = listOf(
            SearchEngine("ChatGPT", "https://chat.openai.com/", R.drawable.ic_search, "ChatGPT"),
            SearchEngine("GPT-4o", "https://chat.openai.com/?model=gpt-4o", R.drawable.ic_bing, "GPT-4o"), // 使用必应图标
            SearchEngine("Claude", "https://claude.ai/", R.drawable.ic_baidu, "Claude AI助手"), // 使用百度图标
            SearchEngine("文心一言", "https://yiyan.baidu.com/", R.drawable.ic_baidu, "文心一言"),
            SearchEngine("通义千问", "https://qianwen.aliyun.com/", R.drawable.ic_google, "阿里通义千问"), // 使用谷歌图标
            SearchEngine("讯飞星火", "https://xinghuo.xfyun.cn/", R.drawable.ic_sogou, "讯飞星火"), // 使用搜狗图标
            SearchEngine("必应AI", "https://www.bing.com/new", R.drawable.ic_bing, "必应AI聊天"),
            SearchEngine("搜狗AI", "https://ai.sogou.com/", R.drawable.ic_sogou, "搜狗AI助手"),
            SearchEngine("Gemini", "https://gemini.google.com/", R.drawable.ic_google, "Google Gemini"),
            SearchEngine("Perplexity", "https://www.perplexity.ai/", R.drawable.ic_360, "Perplexity AI") // 使用360图标
        )
        
        Log.d(TAG, "添加常规搜索引擎图标，数量: ${normalEngines.size}")
        // 添加常规搜索引擎图标
        addSearchEngineIcons(normalEngineContainer, normalEngines, webView, defaultEngine, false)
        
        Log.d(TAG, "添加AI搜索引擎图标，数量: ${aiEngines.size}")
        // 添加AI搜索引擎图标
        addSearchEngineIcons(aiEngineContainer, aiEngines, webView, defaultEngine, true)
        
        // 确保容器背景颜色不同，以便于区分
        normalEngineContainer.setBackgroundColor(Color.parseColor("#10000000"))
        aiEngineContainer.setBackgroundColor(Color.parseColor("#10303F9F"))
        
        // 找到对应的ScrollContainer
        val aiScrollContainer = when (webView) {
            firstWebView -> firstAIScrollContainer
            secondWebView -> secondAIScrollContainer
            thirdWebView -> thirdAIScrollContainer
            else -> null
        }
        
        // 设置两种搜索引擎容器共用同一位置
        val parentLayout = normalEngineContainer.parent as? ViewGroup
        if (parentLayout != null && aiScrollContainer != null) {
            // 确保父容器使用的是FrameLayout或者其他可以重叠子视图的布局
            if (parentLayout is FrameLayout) {
                // 已经是FrameLayout，直接设置布局参数
                val params = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
                
                normalEngineContainer.layoutParams = params
                aiScrollContainer.layoutParams = params
            } else {
                Log.w(TAG, "父容器不是FrameLayout，无法使容器重叠显示")
            }
        }
        
        // 根据默认引擎类型设置初始可见性
        if (isAIEngine) {
            // 如果默认是AI引擎，显示AI引擎容器，隐藏普通引擎容器
            aiScrollContainer?.visibility = View.VISIBLE
            normalEngineContainer.visibility = View.GONE
            toggleButton.setImageResource(R.drawable.ic_search)  // 显示切换到普通搜索的图标
        } else {
            // 如果默认是普通引擎，显示普通引擎容器，隐藏AI引擎容器
            aiScrollContainer?.visibility = View.GONE
            normalEngineContainer.visibility = View.VISIBLE
            toggleButton.setImageResource(R.drawable.ic_ai_search)  // 显示切换到AI搜索的图标
        }
        
        // 记录容器状态
        Log.d(TAG, "容器状态 - AI容器可见性: ${if (aiScrollContainer?.visibility == View.VISIBLE) "可见" else "不可见"}, " +
                "普通容器可见性: ${if (normalEngineContainer.visibility == View.VISIBLE) "可见" else "不可见"}")
        
        // 确保容器有子视图
        Log.d(TAG, "AI容器子视图数量: ${aiEngineContainer.childCount}, " +
                "普通容器子视图数量: ${normalEngineContainer.childCount}")
    }

    /**
     * 添加搜索引擎图标到容器
     */
    private fun addSearchEngineIcons(
        container: LinearLayout,
        engines: List<SearchEngine>,
        webView: WebView,
        defaultEngine: String,
        isAI: Boolean
    ) {
        val prefix = if (isAI) "ai_" else ""
        
        // 确保容器为空，避免重复添加图标
        container.removeAllViews()
        
        // 创建水平滚动布局包裹引擎图标
        val scrollView = HorizontalScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            isHorizontalScrollBarEnabled = true
            setPadding(2.dpToPx(this@DualFloatingWebViewService), 
                      2.dpToPx(this@DualFloatingWebViewService), 
                      2.dpToPx(this@DualFloatingWebViewService), 
                      2.dpToPx(this@DualFloatingWebViewService))
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            isVerticalScrollBarEnabled = false
            
            // 启用平滑滚动
            isSmoothScrollingEnabled = true
            
            // 增加水平滚动条可见度
            scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
            
            // 添加触摸事件监听，使其支持拖动滚动
            setOnTouchListener(object : View.OnTouchListener {
                private var startX = 0f
                private var startScrollX = 0
                private var isDragging = false
                private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
                
                override fun onTouch(v: View, event: MotionEvent): Boolean {
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            // 记录起始触摸位置和滚动位置
                            startX = event.x
                            startScrollX = scrollX
                            isDragging = false
                            // 返回false以允许子视图也接收触摸事件
                            return false
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val deltaX = startX - event.x
                            
                            // 如果移动距离超过阈值，开始拖动
                            if (!isDragging && Math.abs(deltaX) > touchSlop) {
                                isDragging = true
                                // 阻止父视图拦截触摸事件
                                parent.requestDisallowInterceptTouchEvent(true)
                            }
                            
                            if (isDragging) {
                                // 计算新的滚动位置
                                val newScrollX = (startScrollX + deltaX).toInt()
                                // 滚动到新位置
                                scrollTo(newScrollX, 0)
                                return true
                            }
                            return false
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            if (isDragging) {
                                // 在拖动结束时执行惯性滚动
                                // 允许父视图再次拦截触摸事件
                                parent.requestDisallowInterceptTouchEvent(false)
                                return true
                            }
                            return false
                        }
                    }
                    return false
                }
            })
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
            
            // 设置最小宽度，确保容器即使为空也有宽度
            minimumWidth = 100.dpToPx(this@DualFloatingWebViewService)
        }
        
        Log.d(TAG, "添加${if (isAI) "AI" else "普通"}搜索引擎图标，数量: ${engines.size}")
        
        // 添加引擎图标
        engines.forEach { engine ->
            val engineKey = prefix + engine.name.lowercase()
            val iconView = createEngineIconButton(engine, engineKey)
            
            // 加载图标（使用Google的图标服务）
            loadEngineIcon(iconView, engine)
            
            // 设置点击事件
            iconView.setOnClickListener {
                // 执行搜索
                val query = searchInput?.text?.toString() ?: ""
                if (query.isNotEmpty()) {
                    performSearch(webView, query, engine.url)
                } else {
                    // 如果搜索框为空，直接加载引擎首页
                    val baseUrl = engine.url.split("?")[0]
                    webView.loadUrl(baseUrl)
                }
                
                // 更新选中状态
                updateEngineSelection(container, null, engineKey)
                
                // 将选中引擎滚动到视图中央
                val scrollToX = iconView.left - (scrollView.width / 2) + (iconView.width / 2)
                scrollView.smoothScrollTo(Math.max(0, scrollToX), 0)
                
                // 更新对应窗口的默认引擎
                when {
                    webView == firstWebView -> {
                        leftEngineKey = engineKey
                        settingsManager.setLeftWindowSearchEngine(engineKey)
                    }
                    webView == secondWebView -> {
                        centerEngineKey = engineKey
                        settingsManager.setCenterWindowSearchEngine(engineKey)
                    }
                    webView == thirdWebView -> {
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
                
                // 显示提示 - 使用Handler.post确保提示在UI更新后显示
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
            
            Log.d(TAG, "添加了搜索引擎图标: $engineKey")
        }
        
        // 确保容器有最小高度
        iconsContainer.minimumHeight = 50.dpToPx(this)
        
        // 将图标容器添加到滚动视图
        scrollView.addView(iconsContainer)
        
        // 将滚动视图添加到引擎容器
        container.addView(scrollView)
        
        // 确保容器可见性
        container.visibility = View.VISIBLE
        
        // 记录容器内容
        Log.d(TAG, "${if (isAI) "AI" else "普通"}搜索引擎容器初始化完成，子视图数量: ${container.childCount}")
        
        // 如果当前引擎在此容器中，滚动到该位置
        if ((isAI && defaultEngine.startsWith("ai_")) || (!isAI && !defaultEngine.startsWith("ai_"))) {
            scrollView.post {
                // 查找选中的图标
                for (i in 0 until iconsContainer.childCount) {
                    val view = iconsContainer.getChildAt(i)
                    if (view is ImageView && view.tag == defaultEngine) {
                        // 计算滚动位置，使图标居中
                        val scrollToX = view.left - (scrollView.width / 2) + (view.width / 2)
                        scrollView.smoothScrollTo(Math.max(0, scrollToX), 0)
                        break
                    }
                }
            }
        }
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
                
                // 设置切换按钮可见性
                firstEngineToggle?.visibility = View.VISIBLE
                secondEngineToggle?.visibility = View.GONE
                thirdEngineToggle?.visibility = View.GONE
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
                
                // 设置切换按钮可见性
                firstEngineToggle?.visibility = View.VISIBLE
                secondEngineToggle?.visibility = View.VISIBLE
                thirdEngineToggle?.visibility = View.GONE
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
                
                // 设置切换按钮可见性
                firstEngineToggle?.visibility = View.VISIBLE
                secondEngineToggle?.visibility = View.VISIBLE
                thirdEngineToggle?.visibility = View.VISIBLE
            }
        }
        
        // 确保初始化时AI容器的正确可见性
        if (firstAIEngineContainer != null && firstEngineContainer != null) {
            val isAI = leftEngineKey.startsWith("ai_")
            firstAIScrollContainer?.visibility = if (isAI) View.VISIBLE else View.GONE
            firstEngineContainer?.visibility = View.VISIBLE
        }
        
        if (secondAIEngineContainer != null && secondEngineContainer != null) {
            val isAI = centerEngineKey.startsWith("ai_")
            secondAIScrollContainer?.visibility = if (isAI) View.VISIBLE else View.GONE
            secondEngineContainer?.visibility = View.VISIBLE
        }
        
        if (thirdAIEngineContainer != null && thirdEngineContainer != null) {
            val isAI = rightEngineKey.startsWith("ai_")
            thirdAIScrollContainer?.visibility = if (isAI) View.VISIBLE else View.GONE
            thirdEngineContainer?.visibility = View.VISIBLE
        }
        
        // 更新布局
        container?.requestLayout()
    }

    /**
     * 刷新搜索引擎切换按钮的状态和事件
     */
    private fun refreshEngineToggleButtons() {
        Log.d(TAG, "刷新搜索引擎切换按钮")
        
        // 确保切换按钮可见
        firstEngineToggle?.visibility = View.VISIBLE
        secondEngineToggle?.visibility = View.VISIBLE
        thirdEngineToggle?.visibility = View.VISIBLE
        
        // 给第一个窗口切换按钮添加点击事件
        firstEngineToggle?.setOnClickListener {
            try {
                Log.d(TAG, "第一个窗口切换按钮被点击")
                
                // 确保容器存在
                if (firstAIScrollContainer == null || firstEngineContainer == null) {
                    Log.e(TAG, "第一个窗口容器未初始化")
                    Toast.makeText(this, "错误：搜索引擎容器未初始化", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                
                // 获取当前显示的是哪种引擎列表
                val isAIEngineShowing = firstAIScrollContainer?.visibility == View.VISIBLE
                Log.d(TAG, "第一个窗口当前显示的是${if (isAIEngineShowing) "AI" else "普通"}搜索引擎")
                
                // 直接切换显示的引擎列表
                firstAIScrollContainer?.visibility = if (isAIEngineShowing) View.GONE else View.VISIBLE
                firstEngineContainer?.visibility = if (isAIEngineShowing) View.VISIBLE else View.GONE
                
                // 更新切换按钮图标
                firstEngineToggle?.setImageResource(if (isAIEngineShowing) R.drawable.ic_ai_search else R.drawable.ic_search)
                
                // 使用Handler.post确保提示在UI更新后显示，并减少显示延迟感
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(
                        this@DualFloatingWebViewService,
                        "已切换到${if (isAIEngineShowing) "普通" else "AI"}搜索引擎",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "第一个窗口切换按钮出错", e)
                Toast.makeText(this, "切换引擎出错: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
        
        // 给第二个窗口切换按钮添加点击事件
        secondEngineToggle?.setOnClickListener {
            try {
                Log.d(TAG, "第二个窗口切换按钮被点击")
                
                // 确保容器存在
                if (secondAIScrollContainer == null || secondEngineContainer == null) {
                    Log.e(TAG, "第二个窗口容器未初始化")
                    Toast.makeText(this, "错误：搜索引擎容器未初始化", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                
                // 获取当前显示的是哪种引擎列表
                val isAIEngineShowing = secondAIScrollContainer?.visibility == View.VISIBLE
                Log.d(TAG, "第二个窗口当前显示的是${if (isAIEngineShowing) "AI" else "普通"}搜索引擎")
                
                // 直接切换显示的引擎列表
                secondAIScrollContainer?.visibility = if (isAIEngineShowing) View.GONE else View.VISIBLE
                secondEngineContainer?.visibility = if (isAIEngineShowing) View.VISIBLE else View.GONE
                
                // 更新切换按钮图标
                secondEngineToggle?.setImageResource(if (isAIEngineShowing) R.drawable.ic_ai_search else R.drawable.ic_search)
                
                // 使用Handler.post确保提示在UI更新后显示，并减少显示延迟感
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(
                        this@DualFloatingWebViewService,
                        "已切换到${if (isAIEngineShowing) "普通" else "AI"}搜索引擎",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "第二个窗口切换按钮出错", e)
                Toast.makeText(this, "切换引擎出错: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
        
        // 给第三个窗口切换按钮添加点击事件
        thirdEngineToggle?.setOnClickListener {
            try {
                Log.d(TAG, "第三个窗口切换按钮被点击")
                
                // 确保容器存在
                if (thirdAIScrollContainer == null || thirdEngineContainer == null) {
                    Log.e(TAG, "第三个窗口容器未初始化")
                    Toast.makeText(this, "错误：搜索引擎容器未初始化", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                
                // 获取当前显示的是哪种引擎列表
                val isAIEngineShowing = thirdAIScrollContainer?.visibility == View.VISIBLE
                Log.d(TAG, "第三个窗口当前显示的是${if (isAIEngineShowing) "AI" else "普通"}搜索引擎")
                
                // 直接切换显示的引擎列表
                thirdAIScrollContainer?.visibility = if (isAIEngineShowing) View.GONE else View.VISIBLE
                thirdEngineContainer?.visibility = if (isAIEngineShowing) View.VISIBLE else View.GONE
                
                // 更新切换按钮图标
                thirdEngineToggle?.setImageResource(if (isAIEngineShowing) R.drawable.ic_ai_search else R.drawable.ic_search)
                
                // 使用Handler.post确保提示在UI更新后显示，并减少显示延迟感
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(
                        this@DualFloatingWebViewService,
                        "已切换到${if (isAIEngineShowing) "普通" else "AI"}搜索引擎",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "第三个窗口切换按钮出错", e)
                Toast.makeText(this, "切换引擎出错: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
        
        Log.d(TAG, "搜索引擎切换按钮刷新完成")
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
                // 移除FLAG_NOT_FOCUSABLE标志，使窗口可以获取焦点
                params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            } else {
                // 添加FLAG_NOT_FOCUSABLE标志，使窗口不可获取焦点
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            }
            
            // 应用新参数
            try {
                windowManager.updateViewLayout(floatingView, params)
            } catch (e: Exception) {
                Log.e(TAG, "更新窗口布局参数失败", e)
            }
            
            // 如果切换到可获取焦点，则请求焦点
            if (focusable) {
                floatingView?.requestFocus()
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
} 