
package com.example.aifloatingball

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AlertDialog
import com.example.aifloatingball.voice.VoiceInputManager
import com.example.aifloatingball.adapter.AppSelectionDialogAdapter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.Toast
import android.view.GestureDetector
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import kotlin.math.abs
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.widget.*
import android.widget.ArrayAdapter
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.drawerlayout.widget.DrawerLayout
import androidx.viewpager2.widget.ViewPager2
import androidx.core.view.GravityCompat
import androidx.core.view.GestureDetectorCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import java.io.File
import org.json.JSONObject
import org.json.JSONArray
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.aifloatingball.manager.ModeManager
import com.example.aifloatingball.manager.AIApiManager
import com.example.aifloatingball.manager.AIServiceType
import com.example.aifloatingball.SettingsManager
import com.example.aifloatingball.manager.SimpleChatHistoryManager
import com.example.aifloatingball.adapter.TaskTemplateAdapter
import com.example.aifloatingball.data.SimpleTaskTemplates
import com.example.aifloatingball.model.PromptTemplate
import com.example.aifloatingball.model.PromptField
import com.example.aifloatingball.model.FieldType
import com.example.aifloatingball.model.UserPromptData
import com.example.aifloatingball.model.ChatContact
import com.example.aifloatingball.model.ContactType
import com.example.aifloatingball.model.ContactCategory
import com.example.aifloatingball.adapter.ChatContactAdapter
import com.example.aifloatingball.manager.AITagManager
import com.example.aifloatingball.model.AppSearchConfig
import com.example.aifloatingball.model.AppCategory
import com.example.aifloatingball.model.AppSearchSettings
import com.example.aifloatingball.adapter.AppSearchGridAdapter
import com.example.aifloatingball.utils.FaviconLoader
import com.example.aifloatingball.ui.TabSwitchAnimationManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import java.net.HttpURLConnection
import java.net.URL
import android.content.BroadcastReceiver
import android.content.IntentFilter

import com.example.aifloatingball.service.SimpleModeService
import com.example.aifloatingball.service.FloatingWindowService
import com.example.aifloatingball.service.DynamicIslandService
import com.example.aifloatingball.service.DualFloatingWebViewService
import com.example.aifloatingball.voice.VoicePromptBranchManager
import com.example.aifloatingball.dialog.NewCardSelectionDialog
import com.example.aifloatingball.model.HistoryEntry
import com.example.aifloatingball.model.BookmarkEntry
import com.example.aifloatingball.webview.MultiPageWebViewManager
import com.example.aifloatingball.webview.CardWebViewManager
import com.example.aifloatingball.webview.GestureCardWebViewManager
import com.example.aifloatingball.webview.MobileCardManager
import com.example.aifloatingball.manager.GroupChatManager
import com.example.aifloatingball.manager.UnifiedGroupChatManager
import com.example.aifloatingball.manager.GroupChatDataChangeListener
import com.example.aifloatingball.manager.GroupChatDataChangeEvent
import com.example.aifloatingball.model.MemberType
import com.example.aifloatingball.model.GroupChat
import com.example.aifloatingball.views.WebViewTabBar
import com.example.aifloatingball.views.MaterialSearchEngineSelector
import com.example.aifloatingball.views.CardPreviewOverlay
import com.example.aifloatingball.views.QuarterArcOperationBar

import com.google.android.material.switchmaterial.SwitchMaterial
import android.widget.EditText
import android.widget.Button
import androidx.appcompat.app.AppCompatDelegate
import android.provider.Settings
import android.os.Build

class SimpleModeActivity : AppCompatActivity(), VoicePromptBranchManager.BranchViewListener, GroupChatDataChangeListener {

    companion object {
        private const val TAG = "SimpleModeActivity"
        // 为交互模式定义一个存储键
        private const val KEY_VOICE_INTERACTION_MODE = "voice_interaction_mode"
        // 用于保存当前界面状态的键
        private const val KEY_CURRENT_STATE = "current_state"
        // 系统语音输入请求码
        private const val SYSTEM_VOICE_REQUEST_CODE = 1002
        // 联系人数据持久化相关
        private const val CONTACTS_PREFS_NAME = "chat_contacts"
        private const val KEY_SAVED_CONTACTS = "saved_contacts"
        // Activity请求码
        private const val REQUEST_CODE_ADD_AI_CONTACT = 1101
    }

    /**
     * 安全启动Activity的通用方法
     */
    private fun safeStartActivity(activityClass: Class<*>, activityName: String) {
        try {
            Log.d(TAG, "尝试启动$activityName")

            // 检查Activity类是否存在
            val intent = Intent(this, activityClass)

            // 检查是否有可以处理这个Intent的Activity
            val resolveInfo = packageManager.resolveActivity(intent, 0)
            if (resolveInfo == null) {
                Log.e(TAG, "找不到可以处理Intent的Activity: $activityName")
                Toast.makeText(this, "无法找到$activityName", Toast.LENGTH_LONG).show()
                return
            }

            Log.d(TAG, "Intent验证成功，准备启动$activityName")
            startActivity(intent)
            Log.d(TAG, "$activityName 启动成功")

        } catch (e: SecurityException) {
            Log.e(TAG, "启动$activityName 时权限不足", e)
            Toast.makeText(this, "启动$activityName 权限不足", Toast.LENGTH_LONG).show()
        } catch (e: android.content.ActivityNotFoundException) {
            Log.e(TAG, "找不到$activityName", e)
            Toast.makeText(this, "找不到$activityName", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e(TAG, "启动$activityName 失败", e)
            Toast.makeText(this, "启动$activityName 失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // 界面状态
    private enum class UIState {
        CHAT,              // 对话页面
        BROWSER,           // 搜索页面
        TASK_SELECTION,    // 任务选择页面
        STEP_GUIDANCE,     // 步骤引导页面
        PROMPT_PREVIEW,    // 提示预览页面
        VOICE,             // 语音页面
        APP_SEARCH,        // 应用搜索页面
        SETTINGS           // 设置页面
    }

    private var currentState = UIState.TASK_SELECTION
    private var currentTemplate: PromptTemplate? = null
    private var currentStepIndex = 0
    private lateinit var userPromptData: UserPromptData
    private lateinit var settingsManager: SettingsManager
    
    // 会话级别的恢复对话框标记，防止重复弹出
    private var hasShownRestoreDialog = false

    // UI组件
    private lateinit var chatLayout: LinearLayout
    private lateinit var taskSelectionLayout: LinearLayout
    private lateinit var stepGuidanceLayout: LinearLayout
    private lateinit var promptPreviewLayout: LinearLayout
    private lateinit var voiceLayout: ScrollView
    private lateinit var appSearchLayout: androidx.coordinatorlayout.widget.CoordinatorLayout
    private lateinit var browserLayout: androidx.drawerlayout.widget.DrawerLayout
    private lateinit var settingsLayout: ScrollView
    // private lateinit var modeSwitchWidget: ModeSwitchWidget  // 暂时禁用

    // 任务选择页面组件
    private lateinit var taskRecyclerView: RecyclerView
    private lateinit var taskAdapter: TaskTemplateAdapter
    private lateinit var directSearchInput: EditText
    private lateinit var directSearchButton: ImageButton

    // 步骤引导页面组件
    private lateinit var stepTitleText: TextView
    private lateinit var stepQuestionText: TextView
    private lateinit var stepInputLayout:                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              LinearLayout
    private lateinit var stepInputText: EditText
    private lateinit var stepChoiceGroup: RadioGroup
    private lateinit var stepMultiChoiceLayout: LinearLayout
    private lateinit var nextStepButton: MaterialButton
    private lateinit var prevStepButton: MaterialButton
    private lateinit var skipStepButton: MaterialButton

    // Prompt预览页面组件
    private lateinit var finalPromptText: TextView
    private lateinit var recommendedEnginesText: TextView
    private lateinit var executeSearchButton: MaterialButton
    private lateinit var backToTasksButton: MaterialButton

    // 语音页面组件
    private lateinit var voiceMicContainer: MaterialCardView
    private lateinit var voiceMicIcon: ImageView
    private lateinit var voiceStatusText: TextView
    private lateinit var voiceTextInputLayout: LinearLayout
    private lateinit var voiceTextInput: EditText
    private lateinit var voiceClearButton: MaterialButton
    private lateinit var voiceSearchButton: MaterialButton
    private lateinit var voiceInteractionModeSwitch: com.google.android.material.switchmaterial.SwitchMaterial

    // 需要根据语音支持情况控制显示的UI元素
    private var voiceHintText: TextView? = null
    private var voiceSettingsCard: MaterialCardView? = null

    // 应用搜索页面组件
    private lateinit var appCategorySidebar: LinearLayout
    private lateinit var appSearchInput: EditText
    private lateinit var appSearchHint: TextView
    private lateinit var appSearchGrid: RecyclerView
    private lateinit var appSearchAdapter: AppSearchGridAdapter
    private var currentAppCategory = AppCategory.ALL
    private var currentAppConfigs = mutableListOf<AppSearchConfig>()
    private lateinit var appSearchSettings: AppSearchSettings
    private lateinit var searchHistoryManager: SearchHistoryManager
    private lateinit var categoryDragHelper: CategoryDragHelper
    private lateinit var appSelectionHistoryManager: AppSelectionHistoryManager

    // 新的应用图标显示组件
    private lateinit var selectedAppIconContainer: com.google.android.material.card.MaterialCardView
    private lateinit var selectedAppIcon: ImageView

    // 浏览器页面组件 - 多页面WebView版本
    private lateinit var browserWebViewContainer: FrameLayout
    private lateinit var browserHomeContent: LinearLayout
    private lateinit var browserBtnClose: ImageButton
    private lateinit var browserSearchInput: EditText
    private lateinit var browserBtnMenu: ImageButton
    private lateinit var browserProgressBar: ProgressBar
    private lateinit var browserTabContainer: LinearLayout
    private lateinit var browserTabBar: WebViewTabBar
    private lateinit var browserNewTabButton: ImageButton

    // 多页面WebView管理器
    private var multiPageWebViewManager: MultiPageWebViewManager? = null

    // 聊天联系人相关
    private var chatContactAdapter: ChatContactAdapter? = null
    private var allContacts = mutableListOf<ContactCategory>()
    private var currentFilteredContacts = mutableListOf<ContactCategory>()

    // 标签切换动画管理器
    private val tabSwitchAnimationManager = TabSwitchAnimationManager()
    private var currentTabPosition = 0

    // 标签页拖拽状态标志
    private var isDraggingTabs = false

    // 标签页初始化完成标志
    private var isTabsInitialized = false



    // 手势卡片式WebView管理器
    private var gestureCardWebViewManager: GestureCardWebViewManager? = null

    // 新的手机卡片管理器
    private var mobileCardManager: MobileCardManager? = null

    // 四分之一圆弧操作栏
    private var quarterArcOperationBar: QuarterArcOperationBar? = null

    // API密钥同步广播接收器
    private var apiKeySyncReceiver: BroadcastReceiver? = null
    private var addAIContactReceiver: BroadcastReceiver? = null
    
    // 群聊管理器
    // 统一群聊管理器
    private lateinit var unifiedGroupChatManager: UnifiedGroupChatManager


    private lateinit var browserShortcutsGrid: androidx.recyclerview.widget.RecyclerView
    private lateinit var browserGestureHint: TextView
    private lateinit var browserNavDrawer: LinearLayout
    private lateinit var browserLetterTitle: TextView
    private lateinit var browserPreviewEngineList: androidx.recyclerview.widget.RecyclerView
    private lateinit var draggableEngineAdapter: com.example.aifloatingball.adapter.DraggableSearchEngineAdapter
    private lateinit var browserExitButton: Button
    private lateinit var browserLetterIndexBar: com.example.aifloatingball.view.LetterIndexBar
    private lateinit var browserSearchEngineSelector: MaterialSearchEngineSelector
    private lateinit var browserPreviewCardsButton: MaterialButton
    private lateinit var browserGestureOverlay: FrameLayout
    private lateinit var browserGestureHintClose: MaterialButton
    private lateinit var cardPreviewOverlay: CardPreviewOverlay

    // 搜索tab手势遮罩区相关
    private var searchTabGestureOverlay: FrameLayout? = null
    private var isSearchTabGestureOverlayActive = false
    private var gestureDetectorForOverlay: GestureDetectorCompat? = null
    private var hasShownGestureInstructions = false // 标记是否已显示过手势操作指南

    // 手势状态跟踪
    private var isLongPressDetected = false
    private var isDoubleTapDetected = false

    // 浏览器功能相关
    private lateinit var browserGestureDetector: GestureDetectorCompat
    private var currentSearchEngine: com.example.aifloatingball.model.SearchEngine? = null

    // Material波浪追踪器（已弃用）
    private var materialWaveTracker: com.example.aifloatingball.views.MaterialWaveTracker? = null

    // 层叠卡片预览器
    private var stackedCardPreview: com.example.aifloatingball.views.StackedCardPreview? = null



    // 隐藏的组件用于兼容性
    private lateinit var browserViewPager: androidx.viewpager2.widget.ViewPager2
    private lateinit var browserTabPreviewContainer: LinearLayout
    private lateinit var browserBtnAddTab: ImageButton
    private lateinit var browserAppbar: com.google.android.material.appbar.AppBarLayout
    private lateinit var browserVoiceSearch: ImageButton
    private lateinit var browserBottomBar: LinearLayout
    private lateinit var browserLeftButtons: LinearLayout
    private lateinit var browserRightButtons: LinearLayout
    private lateinit var browserBtnHistory: ImageButton
    private lateinit var browserBtnBookmarks: ImageButton
    private lateinit var browserBtnSettings: ImageButton
    private lateinit var browserAutoHideSwitch: androidx.appcompat.widget.SwitchCompat
    private lateinit var browserClipboardSwitch: androidx.appcompat.widget.SwitchCompat

    // 语音识别相关
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var recognizedText = ""
    private lateinit var voiceInputManager: VoiceInputManager
    private var voiceSupportInfo: VoiceInputManager.VoiceSupportInfo? = null
    private val handler = Handler(Looper.getMainLooper())

    // 语音提示分支管理器
    private lateinit var promptBranchManager: VoicePromptBranchManager

    // 长按处理相关
    private val longPressHandler = Handler(Looper.getMainLooper())
    private var isLongPress = false
    private val longPressRunnable = Runnable {
        isLongPress = true
        // 显示提示分支界面
        showPromptBranches()
    }

    // 设置页面组件 - 扩展所有设置选项
    private lateinit var displayModeSpinner: Spinner
    private lateinit var windowCountSpinner: Spinner
    private lateinit var clipboardMonitorSwitch: SwitchMaterial
    private lateinit var autoHideSwitch: SwitchMaterial
    private lateinit var aiModeSwitch: SwitchMaterial
    private lateinit var themeModeSpinner: Spinner
    private lateinit var ballAlphaSeekbar: SeekBar
    private lateinit var leftHandedSwitch: SwitchMaterial
    private lateinit var autoPasteSwitch: SwitchMaterial
    private lateinit var multiTabBrowserSwitch: SwitchMaterial
    private lateinit var notificationListenerSwitch: SwitchMaterial


    private lateinit var floatingBallSettingsContainer: MaterialCardView
    private lateinit var aiApiSettingsItem: LinearLayout
    private lateinit var searchEngineSettingsItem: LinearLayout
    private lateinit var aiSearchEngineSettingsItem: LinearLayout
    private lateinit var masterPromptSettingsItem: LinearLayout
    private lateinit var appSearchSettingsItem: LinearLayout
    private lateinit var permissionManagementItem: LinearLayout
    private lateinit var viewSearchHistoryItem: LinearLayout
    private lateinit var onboardingGuideItem: LinearLayout
    private lateinit var appVersionText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(TAG, "onCreate called")

        // 初始化SettingsManager
        settingsManager = SettingsManager.getInstance(this)
        
        // 初始化统一群聊管理器
        unifiedGroupChatManager = UnifiedGroupChatManager.getInstance(this)
        
        // 设置数据变更监听器
        unifiedGroupChatManager.addDataChangeListener(this)

        // 初始化语音提示分支管理器
        promptBranchManager = VoicePromptBranchManager(this, this)
        // 关键：从设置中读取并应用保存的交互模式
        val savedMode = settingsManager.getString(KEY_VOICE_INTERACTION_MODE, "CLICK")
        promptBranchManager.interactionMode = if (savedMode == "DRAG") {
            VoicePromptBranchManager.InteractionMode.DRAG
        } else {
            VoicePromptBranchManager.InteractionMode.CLICK
        }

        // 确保 SimpleModeService 不在运行
        if (SimpleModeService.isRunning(this)) {
            Log.d(TAG, "SimpleModeService is running, stopping it")
            stopService(Intent(this, SimpleModeService::class.java))
        }

        // 确保我们处于简易模式显示模式
        val previousMode = settingsManager.getDisplayMode()
        Log.d(TAG, "Previous display mode: $previousMode, setting to simple_mode")
        settingsManager.setDisplayMode("simple_mode")

        // 检查权限状态
        checkOverlayPermission()

        // 应用主题设置
        applyTheme()

        setContentView(R.layout.activity_simple_mode)

        // 应用UI颜色
        updateUIColors()

        initializeViews()
        setupTaskSelection()
        setupChat()

        // 注册API密钥同步广播接收器
        setupApiKeySyncReceiver()

        // 处理从小组件传入的参数
        handleWidgetIntent(intent)

        // 注册添加AI联系人广播接收器
        setupAddAIContactReceiver()
        
        // 注册AI对话更新广播接收器
        setupAIChatUpdateReceiver()
        
        // 启动文件监听同步机制
        startFileSyncMonitoring()
        
        // 启动定时强制同步机制
        startPeriodicSync()
        
        // 注册广播接收器
        registerBroadcastReceiver()
        
        // 调试：检查所有AI的数据状态
        debugAllAIData()
        
        // 添加测试按钮来手动刷新数据
        addTestRefreshButton()

        // 检查是否需要恢复之前的状态
        val savedState = savedInstanceState?.getString(KEY_CURRENT_STATE)
        if (savedState != null) {
            Log.d(TAG, "Restoring saved state: $savedState")
            restoreState(savedState)
        } else {
            // 检查是否有保存的悬浮卡片状态
            val sharedPreferences = getSharedPreferences("gesture_cards_state", MODE_PRIVATE)
            val savedCardUrls = sharedPreferences.getStringSet("floating_card_urls", emptySet())
            
            Log.d(TAG, "onCreate: 检查保存的悬浮卡片状态")
            Log.d(TAG, "onCreate: savedCardUrls = $savedCardUrls")
            Log.d(TAG, "onCreate: savedCardUrls?.size = ${savedCardUrls?.size}")
            
            if (savedCardUrls?.isNotEmpty() == true) {
                Log.d(TAG, "Found saved cards, showing browser")
                showBrowser()
            } else {
                Log.d(TAG, "No saved state or cards, showing chat")
                showChat()
            }
        }

        // 处理从其他地方传入的搜索内容
        handleIntentData()

        // 初始化时应用左手模式布局方向
        applyLayoutDirection(settingsManager.isLeftHandedModeEnabled())
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        // 配置变化时重新应用颜色
        updateUIColors()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        Log.d(TAG, "onNewIntent called")

        // 更新当前Intent
        setIntent(intent)

        // 处理新的小组件Intent
        handleWidgetIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // 保存当前界面状态
        outState.putString(KEY_CURRENT_STATE, currentState.name)
        Log.d(TAG, "Saving state: ${currentState.name}")
    }

    /**
     * 恢复保存的界面状态
     */
    private fun restoreState(stateName: String) {
        try {
            val state = UIState.valueOf(stateName)
            Log.d(TAG, "Restoring to state: $state")

            when (state) {
                UIState.CHAT -> showChat()
                UIState.TASK_SELECTION -> showTaskSelection()
                UIState. STEP_GUIDANCE -> {
                    // 如果有保存的任务数据，恢复到步骤引导页面
                    // 否则回到任务选择页面
                    if (::userPromptData.isInitialized) {
                        showStepGuidance()
                    } else {
                        showTaskSelection()
                    }
                }
                UIState.PROMPT_PREVIEW -> {
                    // 如果有保存的提示词数据，恢复到预览页面
                    // 否则回到任务选择页面
                    if (::userPromptData.isInitialized) {
                        showPromptPreview()
                    } else {
                        showTaskSelection()
                    }
                }
                UIState.VOICE -> showVoice()
                UIState.BROWSER -> showBrowser()
                UIState.APP_SEARCH -> showAppSearch()
                UIState.SETTINGS -> showSettings()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring state: $stateName", e)
            // 如果恢复失败，回到默认状态
            showChat()
        }
    }

    private fun checkOverlayPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val hasPermission = android.provider.Settings.canDrawOverlays(this)
            Log.d(TAG, "Overlay permission status: $hasPermission")
            // 移除权限提醒，只记录日志
            if (!hasPermission) {
                Log.w(TAG, "Overlay permission not granted, but continuing without notification")
            }
        } else {
            Log.d(TAG, "Android version < M, no overlay permission check needed")
        }
    }

    /**
     * 应用主题设置
     */
    private fun applyTheme() {
        val themeMode = settingsManager.getThemeMode()
        val targetNightMode = when (themeMode) {
            SettingsManager.THEME_MODE_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            SettingsManager.THEME_MODE_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }

        // 只有当目标模式与当前模式不同时才应用新主题
        val currentNightMode = AppCompatDelegate.getDefaultNightMode()
        if (currentNightMode != targetNightMode) {
            Log.d(TAG, "Applying theme change: $currentNightMode -> $targetNightMode")
            AppCompatDelegate.setDefaultNightMode(targetNightMode)

            // 延迟更新UI颜色，确保主题已经应用
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                updateUIColors()
            }, 100)
        } else {
            Log.d(TAG, "Theme mode unchanged: $targetNightMode")
            // 即使主题模式没有改变，也要更新UI颜色以确保正确显示
            updateUIColors()
        }
    }

    /**
     * 检查是否为暗色模式
     */
    private fun isDarkMode(): Boolean {
        return (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
               android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    /**
     * 动态更新界面颜色
     */
    private fun updateUIColors() {
        try {
            val isDarkMode = isDarkMode()

            Log.d(TAG, "更新UI颜色 - 暗色模式: $isDarkMode")

            // 更新状态栏和导航栏颜色
            val backgroundColor = androidx.core.content.ContextCompat.getColor(this, R.color.simple_mode_background_light)
            window.statusBarColor = backgroundColor
            window.navigationBarColor = backgroundColor

            // 更新根布局背景
            findViewById<View>(android.R.id.content)?.setBackgroundColor(backgroundColor)

            // 更新主布局背景
            val mainLayout = findViewById<LinearLayout>(R.id.simple_mode_main_layout)
            mainLayout?.setBackgroundColor(backgroundColor)

            // 更新各个页面布局的背景
            updatePageBackgrounds()

            // 更新对话页面颜色
            updateChatPageColors()

            // 更新软件tab页面颜色
            updateAppSearchPageColors()

            // 更新标题栏颜色
            updateHeaderColors()

            // 更新底部导航颜色
            updateBottomNavigationColors()

            // 更新所有文本颜色
            updateAllTextColors()

            // 更新输入框和按钮颜色
            updateInputAndButtonColors()

            // 更新卡片背景颜色
            updateCardBackgrounds()

        } catch (e: Exception) {
            android.util.Log.e("SimpleModeActivity", "Error updating UI colors", e)
        }
    }

    /**
     * 更新所有文本颜色
     */
    private fun updateAllTextColors() {
        // 递归更新所有TextView的颜色
        val rootView = findViewById<ViewGroup>(android.R.id.content)
        updateTextColorsRecursively(rootView)
    }

    /**
     * 递归更新ViewGroup中所有TextView的颜色
     */
    private fun updateTextColorsRecursively(viewGroup: ViewGroup) {
        val isDarkMode = isDarkMode()
        for (i in 0 until viewGroup.childCount) {
            val child = viewGroup.getChildAt(i)
            when (child) {
                is TextView -> {
                    // 根据TextView的用途设置不同的颜色
                    when (child.id) {
                        R.id.final_prompt_text -> {
                            child.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.simple_mode_text_primary_light))
                        }
                        R.id.recommended_engines_text -> {
                            child.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.simple_mode_text_secondary_light))
                        }
                        else -> {
                            // 默认使用主要文本颜色
                            child.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.simple_mode_text_primary_light))
                        }
                    }
                }
                is ViewGroup -> {
                    // 递归处理子ViewGroup
                    updateTextColorsRecursively(child)
                }
            }
        }
    }

    /**
     * 更新页面背景颜色
     */
    private fun updatePageBackgrounds() {
        val isDarkMode = isDarkMode()
        val backgroundColor = androidx.core.content.ContextCompat.getColor(this, R.color.simple_mode_background_light)

        // 更新各个页面布局的背景
        findViewById<LinearLayout>(R.id.step_guidance_layout)?.setBackgroundColor(backgroundColor)
        findViewById<LinearLayout>(R.id.prompt_preview_layout)?.setBackgroundColor(backgroundColor)
        findViewById<ScrollView>(R.id.voice_layout)?.setBackgroundColor(backgroundColor)
        findViewById<ScrollView>(R.id.settings_layout)?.setBackgroundColor(backgroundColor)
        findViewById<LinearLayout>(R.id.browser_layout)?.setBackgroundColor(backgroundColor)
        findViewById<View>(R.id.app_search_layout)?.setBackgroundColor(backgroundColor)
    }

    /**
     * 更新输入框和按钮颜色
     */
    private fun updateInputAndButtonColors() {
        val isDarkMode = isDarkMode()
        // 更新搜索框背景
        val inputBackground = androidx.core.content.ContextCompat.getColor(this, R.color.simple_mode_input_background_light)
        val inputTextColor = androidx.core.content.ContextCompat.getColor(this, R.color.simple_mode_text_primary_light)
        val inputHintColor = androidx.core.content.ContextCompat.getColor(this, R.color.simple_mode_text_secondary_light)

        // 直接搜索输入框
        findViewById<EditText>(R.id.direct_search_input)?.apply {
            setTextColor(inputTextColor)
            setHintTextColor(inputHintColor)
            setBackgroundColor(inputBackground)
        }

        // 步骤输入框
        findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.step_input_text)?.apply {
            setTextColor(inputTextColor)
            setBackgroundColor(inputBackground)
        }

        // 语音输入框
        findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.voice_text_input)?.apply {
            setTextColor(inputTextColor)
            setBackgroundColor(inputBackground)
        }
    }

    /**
     * 更新卡片背景颜色
     */
    private fun updateCardBackgrounds() {
        val isDarkMode = isDarkMode()
        val cardBackground = androidx.core.content.ContextCompat.getColor(this, R.color.simple_mode_card_background_light)

        // 更新Prompt预览页面中的卡片
        findViewById<androidx.cardview.widget.CardView>(R.id.prompt_preview_layout)?.let { cardView ->
            cardView.setCardBackgroundColor(cardBackground)
        }

        // 更新语音页面中的卡片
        val voiceLayout = findViewById<ScrollView>(R.id.voice_layout)
        voiceLayout?.let { updateCardBackgroundsRecursively(it, cardBackground) }

        // 更新设置页面中的卡片
        val settingsLayout = findViewById<ScrollView>(R.id.settings_layout)
        settingsLayout?.let { updateCardBackgroundsRecursively(it, cardBackground) }

        // 更新浏览器页面中的卡片和搜索框背景
        updateBrowserPageColors()
    }

    /**
     * 更新浏览器页面颜色
     */
    private fun updateBrowserPageColors() {
        val cardBackground = androidx.core.content.ContextCompat.getColor(this, R.color.simple_mode_card_background_light)
        val inputBackground = androidx.core.content.ContextCompat.getColor(this, R.color.simple_mode_input_background_light)
        val textColor = androidx.core.content.ContextCompat.getColor(this, R.color.simple_mode_text_primary_light)

        // 更新浏览器页面的搜索框背景
        val browserLayout = findViewById<LinearLayout>(R.id.browser_layout)
        browserLayout?.let { layout ->
            // 递归更新浏览器页面中的所有元素
            updateBrowserElementsRecursively(layout, cardBackground, inputBackground, textColor)
        }
    }

    /**
     * 递归更新浏览器页面元素
     */
    private fun updateBrowserElementsRecursively(view: View, cardBackground: Int, inputBackground: Int, textColor: Int) {
        when (view) {
            is com.google.android.material.card.MaterialCardView -> {
                view.setCardBackgroundColor(cardBackground)
            }
            is androidx.cardview.widget.CardView -> {
                view.setCardBackgroundColor(cardBackground)
            }
            is LinearLayout -> {
                // 检查是否是搜索框容器
                if (view.background != null) {
                    view.setBackgroundColor(inputBackground)
                }
                // 继续递归处理子视图
                for (i in 0 until view.childCount) {
                    updateBrowserElementsRecursively(view.getChildAt(i), cardBackground, inputBackground, textColor)
                }
            }
            is TextView -> {
                view.setTextColor(textColor)
            }
            is ViewGroup -> {
                for (i in 0 until view.childCount) {
                    updateBrowserElementsRecursively(view.getChildAt(i), cardBackground, inputBackground, textColor)
                }
            }
        }
    }

    /**
     * 隐藏悬浮卡片预览并重置其状态
     */
    private fun deactivateStackedCardPreview() {
        stackedCardPreview?.let {
            it.visibility = View.GONE
            it.reset()
            Log.d(TAG, "悬浮卡片预览已停用")
        }
    }

    /**
     * 递归更新卡片背景
     */
    private fun updateCardBackgroundsRecursively(view: View, cardBackground: Int) {
        val inputBackground = androidx.core.content.ContextCompat.getColor(this, R.color.simple_mode_input_background_light)
        val textColor = androidx.core.content.ContextCompat.getColor(this, R.color.simple_mode_text_primary_light)

        when (view) {
            is com.google.android.material.card.MaterialCardView -> {
                view.setCardBackgroundColor(cardBackground)
            }
            is androidx.cardview.widget.CardView -> {
                view.setCardBackgroundColor(cardBackground)
            }
            is EditText -> {
                // 更新输入框背景和文字颜色
                view.setBackgroundColor(inputBackground)
                view.setTextColor(textColor)
                view.setHintTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.simple_mode_text_secondary_light))
            }
            is TextView -> {
                // 更新文字颜色
                view.setTextColor(textColor)
            }
            is LinearLayout -> {
                // 如果LinearLayout有背景，可能是一个容器，更新其背景
                if (view.background != null) {
                    view.setBackgroundColor(cardBackground)
                }
                // 继续递归处理子视图
                for (i in 0 until view.childCount) {
                    updateCardBackgroundsRecursively(view.getChildAt(i), cardBackground)
                }
            }
            is ViewGroup -> {
                for (i in 0 until view.childCount) {
                    updateCardBackgroundsRecursively(view.getChildAt(i), cardBackground)
                }
            }
        }
    }

    /**
     * 更新标题栏颜色 (已移除标题栏，此方法保留以避免调用错误)
     */
    private fun updateHeaderColors() {
        // 标题栏已移除，此方法不再需要执行任何操作
        // 保留此方法以避免其他地方的调用出错
    }

    /**
     * 更新底部导航颜色
     */
    private fun updateBottomNavigationColors() {
        val bottomNav = findViewById<LinearLayout>(R.id.bottom_navigation) ?: return

        // 设置导航栏背景色
        bottomNav.setBackgroundColor(androidx.core.content.ContextCompat.getColor(this, R.color.simple_mode_tab_background_light))

        // 更新所有Tab的颜色
        updateTabColors()
        // 切换tab时，停用悬浮卡片预览
        deactivateStackedCardPreview()
    }

    /**
     * 更新Tab颜色
     */
    private fun updateTabColors() {
        val bottomNav = findViewById<LinearLayout>(R.id.bottom_navigation) ?: return

        for (i in 0 until bottomNav.childCount) {
            val tabView = bottomNav.getChildAt(i) as? LinearLayout ?: continue
            val iconView = tabView.getChildAt(0) as? ImageView ?: continue
            val textView = tabView.getChildAt(1) as? TextView ?: continue

            // 由于底部导航栏始终保持LTR方向，tab顺序在左右手模式下都是一致的
            // 动态处理语音tab的隐藏情况
            val isSelected = when (tabView.id) {
                R.id.tab_chat -> currentState == UIState.CHAT
                R.id.tab_search -> currentState == UIState.BROWSER
                R.id.tab_home -> currentState == UIState.TASK_SELECTION
                R.id.tab_voice -> currentState == UIState.VOICE
                R.id.tab_app_search -> currentState == UIState.APP_SEARCH
                R.id.tab_settings -> currentState == UIState.SETTINGS
                else -> false
            }

            if (isSelected) {
                // 选中状态
                tabView.setBackgroundColor(androidx.core.content.ContextCompat.getColor(this, R.color.simple_mode_tab_selected_light))
                iconView.setColorFilter(androidx.core.content.ContextCompat.getColor(this, R.color.simple_mode_tab_icon_selected_light))
                textView.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.simple_mode_tab_text_selected_light))
            } else {
                // 正常状态
                tabView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                iconView.setColorFilter(androidx.core.content.ContextCompat.getColor(this, R.color.simple_mode_tab_icon_normal_light))
                textView.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.simple_mode_tab_text_normal_light))
            }
        }
    }

    /**
     * 更新对话页面颜色
     */
    private fun updateChatPageColors() {
        val chatLayout = findViewById<LinearLayout>(R.id.chat_layout)
        val searchInput = findViewById<EditText>(R.id.chat_search_input)
        val addButton = findViewById<ImageButton>(R.id.chat_add_contact_button)
        val tabLayout = findViewById<com.google.android.material.tabs.TabLayout>(R.id.chat_tab_layout)
        val recyclerView = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.chat_contacts_recycler_view)

        // 更新背景颜色
        chatLayout?.setBackgroundColor(androidx.core.content.ContextCompat.getColor(this, R.color.chat_background_light))
        recyclerView?.setBackgroundColor(androidx.core.content.ContextCompat.getColor(this, R.color.chat_background_light))

        // 更新搜索框颜色
        searchInput?.apply {
            setTextColor(androidx.core.content.ContextCompat.getColor(this@SimpleModeActivity, R.color.simple_mode_text_primary_light))
            setHintTextColor(androidx.core.content.ContextCompat.getColor(this@SimpleModeActivity, R.color.chat_search_hint_light))
        }

        // 更新添加按钮颜色
        addButton?.setColorFilter(androidx.core.content.ContextCompat.getColor(this, R.color.chat_add_button_light))

        // 更新TabLayout颜色
        tabLayout?.apply {
            setBackgroundColor(androidx.core.content.ContextCompat.getColor(this@SimpleModeActivity, R.color.chat_tab_background_light))
            setTabTextColors(
                androidx.core.content.ContextCompat.getColor(this@SimpleModeActivity, R.color.chat_tab_unselected_light),
                androidx.core.content.ContextCompat.getColor(this@SimpleModeActivity, R.color.chat_tab_selected_light)
            )
            setSelectedTabIndicatorColor(androidx.core.content.ContextCompat.getColor(this@SimpleModeActivity, R.color.chat_tab_selected_light))
        }
    }

    /**
     * 更新软件tab页面颜色
     */
    private fun updateAppSearchPageColors() {
        try {
            val textColor = androidx.core.content.ContextCompat.getColor(this, R.color.simple_mode_text_primary_light)
            val secondaryTextColor = androidx.core.content.ContextCompat.getColor(this, R.color.simple_mode_text_secondary_light)
            val accentColor = androidx.core.content.ContextCompat.getColor(this, R.color.simple_mode_accent_light)
            val cardBackground = androidx.core.content.ContextCompat.getColor(this, R.color.simple_mode_card_background_light)

            // 更新左侧分类导航按钮
            val categorySidebar = findViewById<LinearLayout>(R.id.app_category_sidebar)
            categorySidebar?.let { sidebar ->
                updateCategorySidebarColors(sidebar, textColor, accentColor)
            }

            // 更新搜索框提示文字
            findViewById<TextView>(R.id.app_search_hint)?.setTextColor(secondaryTextColor)

            // 更新搜索输入框颜色
            findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.app_search_input_layout)?.apply {
                boxStrokeColor = accentColor
                hintTextColor = ColorStateList.valueOf(secondaryTextColor)
                setStartIconTintList(ColorStateList.valueOf(secondaryTextColor))
                setEndIconTintList(ColorStateList.valueOf(secondaryTextColor))
            }

            // 更新搜索输入框文字颜色
            findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.app_search_input)?.setTextColor(textColor)

            // 更新卡片背景色
            updateAppSearchCardBackgrounds(cardBackground)

        } catch (e: Exception) {
            Log.e(TAG, "更新软件tab页面颜色失败", e)
        }
    }

    /**
     * 更新分类侧边栏颜色
     */
    private fun updateCategorySidebarColors(sidebar: LinearLayout, textColor: Int, accentColor: Int) {
        try {
            for (i in 0 until sidebar.childCount) {
                val categoryView = sidebar.getChildAt(i) as? LinearLayout ?: continue

                // 更新分类图标颜色
                val iconView = categoryView.getChildAt(0) as? ImageView
                iconView?.setColorFilter(accentColor)

                // 更新分类文字颜色
                val textView = categoryView.getChildAt(1) as? TextView
                textView?.setTextColor(textColor)
            }
        } catch (e: Exception) {
            Log.e(TAG, "更新分类侧边栏颜色失败", e)
        }
    }

    /**
     * 更新软件tab页面的卡片背景色
     */
    private fun updateAppSearchCardBackgrounds(cardBackground: Int) {
        try {
            // 更新搜索框卡片背景
            val appSearchLayout = findViewById<View>(R.id.app_search_layout)
            appSearchLayout?.let { layout ->
                updateCardBackgroundsRecursively(layout, cardBackground)
            }
        } catch (e: Exception) {
            Log.e(TAG, "更新软件tab卡片背景失败", e)
        }
    }

    private fun initializeViews() {
        try {
            // 主要布局 - 使用安全的findViewById
            chatLayout = findViewById<LinearLayout>(R.id.chat_layout)
            taskSelectionLayout = findViewById<LinearLayout>(R.id.task_selection_layout)
            stepGuidanceLayout = findViewById<LinearLayout>(R.id.step_guidance_layout)
            promptPreviewLayout = findViewById<LinearLayout>(R.id.prompt_preview_layout)
            voiceLayout = findViewById<ScrollView>(R.id.voice_layout)
            appSearchLayout = findViewById<androidx.coordinatorlayout.widget.CoordinatorLayout>(R.id.app_search_layout)
            browserLayout = findViewById<androidx.drawerlayout.widget.DrawerLayout>(R.id.browser_layout)
            settingsLayout = findViewById<ScrollView>(R.id.settings_layout)

            Log.d(TAG, "主要布局初始化完成")
        } catch (e: Exception) {
            Log.e(TAG, "初始化主要布局失败", e)
            throw e
        }
        // modeSwitchWidget = findViewById(R.id.mode_switch_widget)  // 暂时禁用

        // 任务选择页面
        try {
            taskRecyclerView = findViewById<RecyclerView>(R.id.task_recycler_view)
            directSearchInput = findViewById<EditText>(R.id.direct_search_input)
            directSearchButton = findViewById<ImageButton>(R.id.direct_search_button)
            Log.d(TAG, "任务选择页面组件初始化完成")
        } catch (e: Exception) {
            Log.e(TAG, "初始化任务选择页面组件失败", e)
        }

        // 步骤引导页面
        try {
            stepTitleText = findViewById<TextView>(R.id.step_title_text)
            stepQuestionText = findViewById<TextView>(R.id.step_question_text)
            stepInputLayout = findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.step_input_layout)
            stepInputText = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.step_input_text)
            stepChoiceGroup = findViewById<RadioGroup>(R.id.step_choice_group)
            stepMultiChoiceLayout = findViewById<LinearLayout>(R.id.step_multi_choice_layout)
            prevStepButton = findViewById<com.google.android.material.button.MaterialButton>(R.id.prev_step_button)
            skipStepButton = findViewById<com.google.android.material.button.MaterialButton>(R.id.skip_step_button)
            nextStepButton = findViewById<com.google.android.material.button.MaterialButton>(R.id.next_step_button)
            Log.d(TAG, "步骤引导页面组件初始化完成")
        } catch (e: Exception) {
            Log.e(TAG, "初始化步骤引导页面组件失败", e)
        }

        // 验证按钮是否正确初始化
        Log.d(TAG, "步骤按钮初始化完成:")
        Log.d(TAG, "  上一步按钮: ${if (::prevStepButton.isInitialized) "已初始化" else "未初始化"}")
        Log.d(TAG, "  下一步按钮: ${if (::nextStepButton.isInitialized) "已初始化" else "未初始化"}")
        Log.d(TAG, "  跳过按钮: ${if (::skipStepButton.isInitialized) "已初始化" else "未初始化"}")

        // Prompt预览页面
        finalPromptText = findViewById(R.id.final_prompt_text)
        recommendedEnginesText = findViewById(R.id.recommended_engines_text)
        executeSearchButton = findViewById(R.id.execute_search_button)
        backToTasksButton = findViewById(R.id.back_to_tasks_button)

        // 语音页面
        voiceMicContainer = findViewById(R.id.voice_mic_container)
        voiceMicIcon = findViewById(R.id.voice_mic_icon)
        voiceStatusText = findViewById(R.id.voice_status_text)
        voiceTextInputLayout = findViewById(R.id.voice_text_input_layout)
        voiceTextInput = findViewById(R.id.voice_text_input)
        voiceClearButton = findViewById(R.id.voice_clear_button)
        voiceSearchButton = findViewById(R.id.voice_search_button)
        voiceInteractionModeSwitch = findViewById(R.id.voice_interaction_mode_switch)

        // 查找需要根据语音支持情况控制的UI元素
        voiceHintText = findViewById(R.id.voice_hint_text)
        voiceSettingsCard = findViewById(R.id.voice_settings_card)

        // 应用搜索页面组件初始化
        try {
            appCategorySidebar = findViewById<LinearLayout>(R.id.app_category_sidebar)
            appSearchInput = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.app_search_input)
            appSearchHint = findViewById<TextView>(R.id.app_search_hint)
            appSearchGrid = findViewById<RecyclerView>(R.id.app_search_grid)
            selectedAppIconContainer = findViewById<com.google.android.material.card.MaterialCardView>(R.id.selected_app_icon_container)
            selectedAppIcon = findViewById<ImageView>(R.id.selected_app_icon)
            appSearchSettings = AppSearchSettings.getInstance(this)
            searchHistoryManager = SearchHistoryManager.getInstance(this)
            categoryDragHelper = CategoryDragHelper(this)
            appSelectionHistoryManager = AppSelectionHistoryManager.getInstance(this)

            // 临时：强制更新到最新配置以显示新增的应用
            // 这将确保用户能看到所有新增的应用
            Log.d(TAG, "强制更新应用配置到最新版本")
            appSearchSettings.forceResetToLatestConfig()

            // 启动图标预加载
            startIconPreloading()
            Log.d(TAG, "应用搜索页面组件初始化完成")
        } catch (e: Exception) {
            Log.e(TAG, "初始化应用搜索页面组件失败", e)
        }

        // 浏览器页面 - 多页面WebView版本组件初始化
        browserWebViewContainer = findViewById(R.id.browser_webview_container)
        browserHomeContent = findViewById(R.id.browser_home_content)
        browserBtnClose = findViewById(R.id.browser_btn_close)
        browserSearchInput = findViewById(R.id.browser_search_input)
        browserBtnMenu = findViewById(R.id.browser_btn_menu)
        browserProgressBar = findViewById(R.id.browser_progress_bar)
        browserTabContainer = findViewById(R.id.browser_tab_container)
        browserTabBar = findViewById(R.id.browser_tab_bar)
        browserNewTabButton = findViewById(R.id.browser_new_tab_button)
        browserShortcutsGrid = findViewById(R.id.browser_shortcuts_grid)

        // 开始浏览按钮
        val browserStartBrowsingButton = findViewById<MaterialButton>(R.id.browser_start_browsing_button)
        browserGestureHint = findViewById(R.id.browser_gesture_hint)
        browserNavDrawer = findViewById(R.id.browser_nav_drawer)
        browserLetterTitle = findViewById(R.id.browser_letter_title)
        browserPreviewEngineList = findViewById(R.id.browser_preview_engine_list)
        browserExitButton = findViewById(R.id.browser_exit_button)
        browserLetterIndexBar = findViewById(R.id.browser_letter_index_bar)
        browserSearchEngineSelector = findViewById(R.id.browser_search_engine_selector)
        browserPreviewCardsButton = findViewById(R.id.browser_preview_cards_button)
        browserGestureOverlay = findViewById(R.id.browser_gesture_overlay)
        browserGestureHintClose = findViewById(R.id.browser_gesture_hint_close)

        // 创建虚拟组件用于兼容性（这些组件在当前布局中不存在）
        browserViewPager = androidx.viewpager2.widget.ViewPager2(this@SimpleModeActivity)
        browserTabPreviewContainer = LinearLayout(this@SimpleModeActivity)
        browserBtnAddTab = ImageButton(this@SimpleModeActivity)
        browserAppbar = com.google.android.material.appbar.AppBarLayout(this@SimpleModeActivity)
        browserVoiceSearch = ImageButton(this@SimpleModeActivity)
        browserBottomBar = LinearLayout(this@SimpleModeActivity)
        browserLeftButtons = LinearLayout(this@SimpleModeActivity)
        browserRightButtons = LinearLayout(this@SimpleModeActivity)
        browserBtnHistory = ImageButton(this@SimpleModeActivity)
        browserBtnBookmarks = ImageButton(this@SimpleModeActivity)
        browserBtnSettings = ImageButton(this@SimpleModeActivity)
        browserAutoHideSwitch = androidx.appcompat.widget.SwitchCompat(this@SimpleModeActivity)
        browserClipboardSwitch = androidx.appcompat.widget.SwitchCompat(this@SimpleModeActivity)



        // 设置页面 - 扩展所有设置选项
        displayModeSpinner = findViewById(R.id.display_mode_spinner)
        windowCountSpinner = findViewById(R.id.window_count_spinner)
        clipboardMonitorSwitch = findViewById(R.id.clipboard_monitor_switch)
        autoHideSwitch = findViewById(R.id.auto_hide_switch)
        aiModeSwitch = findViewById(R.id.ai_mode_switch)
        themeModeSpinner = findViewById(R.id.theme_mode_spinner)
        ballAlphaSeekbar = findViewById(R.id.ball_alpha_seekbar)
        leftHandedSwitch = findViewById(R.id.left_handed_switch)
        autoPasteSwitch = findViewById(R.id.auto_paste_switch)
        multiTabBrowserSwitch = findViewById(R.id.multi_tab_browser_switch)
        notificationListenerSwitch = findViewById(R.id.notification_listener_switch)


        aiApiSettingsItem = findViewById(R.id.ai_api_settings_item)
        searchEngineSettingsItem = findViewById(R.id.search_engine_settings_item)
        aiSearchEngineSettingsItem = findViewById(R.id.ai_search_engine_settings_item)
        masterPromptSettingsItem = findViewById(R.id.master_prompt_settings_item)
        appSearchSettingsItem = findViewById(R.id.app_search_settings_item)
        permissionManagementItem = findViewById(R.id.permission_management_item)
        viewSearchHistoryItem = findViewById(R.id.view_search_history_item)
        onboardingGuideItem = findViewById(R.id.onboarding_guide_item)
        appVersionText = findViewById(R.id.app_version_text)
        floatingBallSettingsContainer = findViewById(R.id.floating_ball_settings_container)

        // 设置搜索框监听器
        setupSearchListeners()

        // 设置搜索引擎RecyclerView
        setupSearchEngineRecyclerView()

        // 设置语音页面
        setupVoicePage()

        // 设置设置页面
        setupSettingsPage()

        // 标题栏按钮已移除，不再需要设置按钮监听器
        Log.d(TAG, "标题栏已移除，跳过按钮设置")

        // 设置底部导航栏
        setupBottomNavigation()

        // 设置浏览器
        setupBrowserWebView()

        // 初始化UI颜色
        updateUIColors()
        updateTabColors()
    }

    private fun setupVoicePage() {
        // 语音交互模式切换
        voiceInteractionModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            promptBranchManager.interactionMode = if (isChecked) {
                Toast.makeText(this, "已切换到拖动模式", Toast.LENGTH_SHORT).show()
                updateVoiceInteractionModeUI(VoicePromptBranchManager.InteractionMode.DRAG)
                // 保存设置
                settingsManager.putString(KEY_VOICE_INTERACTION_MODE, "DRAG")
                VoicePromptBranchManager.InteractionMode.DRAG
            } else {
                Toast.makeText(this, "已切换到点击模式", Toast.LENGTH_SHORT).show()
                updateVoiceInteractionModeUI(VoicePromptBranchManager.InteractionMode.CLICK)
                // 保存设置
                settingsManager.putString(KEY_VOICE_INTERACTION_MODE, "CLICK")
                VoicePromptBranchManager.InteractionMode.CLICK
            }
        }

        // 根据保存的模式初始化开关状态
        val isDragMode = promptBranchManager.interactionMode == VoicePromptBranchManager.InteractionMode.DRAG
        voiceInteractionModeSwitch.isChecked = isDragMode
        updateVoiceInteractionModeUI(promptBranchManager.interactionMode)

        // 设置麦克风监听器
        setupVoiceMicListener()
    }

    /**
     * 更新语音交互模式UI显示
     */
    private fun updateVoiceInteractionModeUI(mode: VoicePromptBranchManager.InteractionMode) {
        val iconView = findViewById<ImageView>(R.id.voice_interaction_mode_icon)
        val descView = findViewById<TextView>(R.id.voice_interaction_mode_desc)

        when (mode) {
            VoicePromptBranchManager.InteractionMode.CLICK -> {
                iconView?.setImageResource(R.drawable.ic_click)
                descView?.text = "点击模式：点击开始/停止录音"
            }
            VoicePromptBranchManager.InteractionMode.DRAG -> {
                iconView?.setImageResource(R.drawable.ic_drag_handle)
                descView?.text = "拖动模式：长按拖动录音"
            }
        }
    }

    private fun setupVoiceMicListener() {
        // 麦克风按钮触摸监听，处理单击和长按
        voiceMicContainer.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isLongPress = false
                    longPressHandler.postDelayed(longPressRunnable, 500) // 500ms算长按
                    true // 消费事件
                }
                MotionEvent.ACTION_MOVE -> {
                     if (isLongPress) {
                        // 如果是长按模式，则将事件转发给分支管理器
                        promptBranchManager.handleDragEvent(event)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    longPressHandler.removeCallbacks(longPressRunnable)
                    if (isLongPress) {
                        // 如果是长按模式，则将UP事件也转发，用于结束拖动
                         promptBranchManager.handleDragEvent(event)
                    } else {
                        // 否则认为是单击
                        toggleVoiceRecognition()
                    }
                    isLongPress = false
                    true // 消费事件
                }
                else -> false
            }
        }

        voiceClearButton.setOnClickListener { clearVoiceText() }
        voiceSearchButton.setOnClickListener { executeVoiceSearch() }
    }

    private fun setupSettingsPage() {
        // 设置模式切换组件 (暂时禁用)
        // setupModeSwitchWidget()

        // 先设置下拉菜单适配器 - 使用自定义布局支持暗色模式
        val displayModeAdapter = ArrayAdapter.createFromResource(
            this,
            R.array.display_mode_entries,
            R.layout.spinner_item
        ).apply {
            setDropDownViewResource(R.layout.dropdown_item)
        }
        displayModeSpinner.adapter = displayModeAdapter
        Log.d(TAG, "显示模式Spinner适配器已设置，项目数量: ${displayModeAdapter.count}")

        val themeAdapter = ArrayAdapter.createFromResource(
            this,
            R.array.theme_mode_entries,
            R.layout.spinner_item
        ).apply {
            setDropDownViewResource(R.layout.dropdown_item)
        }
        themeModeSpinner.adapter = themeAdapter
        Log.d(TAG, "主题模式Spinner适配器已设置，项目数量: ${themeAdapter.count}")

        val windowCountAdapter = ArrayAdapter.createFromResource(
            this,
            R.array.window_count_entries,
            R.layout.spinner_item
        ).apply {
            setDropDownViewResource(R.layout.dropdown_item)
        }
        windowCountSpinner.adapter = windowCountAdapter

        // 设置下拉菜单监听器
        displayModeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val displayModeValues = resources.getStringArray(R.array.display_mode_values)
                if (position < displayModeValues.size) {
                    val selectedMode = displayModeValues[position]
                    val currentMode = settingsManager.getDisplayMode()

                    // 只有当模式真正改变时才执行切换
                    if (selectedMode != currentMode) {
                        Log.d(TAG, "Display mode changed from $currentMode to $selectedMode")
                        settingsManager.setDisplayMode(selectedMode)

                        // 根据选择的模式执行相应的切换操作
                        when (selectedMode) {
                            "floating_ball" -> {
                                // 切换到悬浮球模式
                                switchToFloatingBallMode()
                            }
                            "dynamic_island" -> {
                                // 切换到灵动岛模式
                                switchToDynamicIslandMode()
                            }
                            "simple_mode" -> {
                                // 已经在简易模式，无需切换，但需要更新设置可见性
                                Log.d(TAG, "Already in simple mode")

                                updateFloatingBallSettingsVisibility()
                            }
                        }
                    } else {
                        // 即使模式没有改变，也要确保设置可见性正确
                        updateFloatingBallSettingsVisibility()
                    }
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        themeModeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                // 修复主题模式映射：
                // 数组顺序：[跟随系统, 浅色模式, 深色模式] → 索引 [0, 1, 2]
                // 对应常量：[THEME_MODE_SYSTEM, THEME_MODE_LIGHT, THEME_MODE_DARK]
                val selectedTheme = when(position) {
                    0 -> SettingsManager.THEME_MODE_SYSTEM  // 跟随系统
                    1 -> SettingsManager.THEME_MODE_LIGHT   // 浅色模式
                    2 -> SettingsManager.THEME_MODE_DARK    // 深色模式
                    else -> SettingsManager.THEME_MODE_SYSTEM
                }

                val currentTheme = settingsManager.getThemeMode()
                // 只有当主题真正改变时才应用新主题
                if (selectedTheme != currentTheme) {
                    Log.d(TAG, "Theme mode changed from $currentTheme to $selectedTheme")
                    settingsManager.setThemeMode(selectedTheme)

                    // 应用新主题（Activity会自动保存和恢复状态）
                    applyTheme()
                } else {
                    Log.d(TAG, "Theme mode unchanged: $selectedTheme")
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        windowCountSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                // 窗口数量选项：1, 2, 3
                val windowCount = position + 1
                settingsManager.setDefaultWindowCount(windowCount)
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        // 设置开关监听器
        leftHandedSwitch.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.setLeftHandedMode(isChecked)
            // 立即应用左手模式
            updateLayoutForHandedness(isChecked)
        }

        clipboardMonitorSwitch.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.setClipboardListenerEnabled(isChecked)
        }

        autoHideSwitch.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.setAutoHide(isChecked)
        }

        aiModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.setIsAIMode(isChecked)
        }

        autoPasteSwitch.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.setAutoPasteEnabled(isChecked)
        }

        multiTabBrowserSwitch.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.putBoolean("use_multi_tab_browser", isChecked)
            Log.d(TAG, "多标签页浏览器设置: $isChecked")
        }

        notificationListenerSwitch.setOnCheckedChangeListener { _, isChecked ->
            // 通知监听器设置需要使用通用的putBoolean方法
            settingsManager.putBoolean("enable_notification_listener", isChecked)
        }



        // 设置透明度SeekBar监听器
        ballAlphaSeekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    // 将0-100的进度转换为0-255的alpha值
                    val alphaValue = ((progress / 100.0) * 255).toInt()
                    settingsManager.setBallAlpha(alphaValue)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })



        // 设置点击事件
        aiApiSettingsItem.setOnClickListener {
            try {
                startActivity(Intent(this, AIApiSettingsActivity::class.java))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open AIApiSettings", e)
                Toast.makeText(this, "无法打开AI API设置", Toast.LENGTH_SHORT).show()
            }
        }

        searchEngineSettingsItem.setOnClickListener {
            safeStartActivity(SearchEngineManagerActivity::class.java, "搜索引擎设置")
        }

        aiSearchEngineSettingsItem.setOnClickListener {
            safeStartActivity(AISearchEngineSettingsActivity::class.java, "AI搜索引擎设置")
        }

        masterPromptSettingsItem.setOnClickListener {
            safeStartActivity(MasterPromptSettingsActivity::class.java, "主提示词设置")
        }

        appSearchSettingsItem.setOnClickListener {
            safeStartActivity(com.example.aifloatingball.settings.AppSearchSettingsActivity::class.java, "应用搜索设置")
        }

        permissionManagementItem.setOnClickListener {
            try {
                startActivity(Intent(this, PermissionManagementActivity::class.java))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open PermissionManagement", e)
                Toast.makeText(this, "无法打开权限管理", Toast.LENGTH_SHORT).show()
            }
        }

        viewSearchHistoryItem.setOnClickListener {
            try {
                startActivity(Intent(this, SearchHistoryActivity::class.java))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open SearchHistory", e)
                Toast.makeText(this, "无法打开搜索历史", Toast.LENGTH_SHORT).show()
            }
        }

        onboardingGuideItem.setOnClickListener {
            try {
                startActivity(Intent(this, com.example.aifloatingball.ui.onboarding.OnboardingActivity::class.java))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open OnboardingGuide", e)
                Toast.makeText(this, "无法打开新手指南", Toast.LENGTH_SHORT).show()
            }
        }

        // 设置App版本号
        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            val version = pInfo.versionName
            appVersionText.text = "版本: $version"
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
            appVersionText.text = "版本: N/A"
        }

        // 初始化语音输入管理器
        voiceInputManager = VoiceInputManager(this)
        voiceInputManager.setCallback(object : VoiceInputManager.VoiceInputCallback {
            override fun onVoiceInputResult(text: String) {
                runOnUiThread {
                    // 将识别结果添加到现有文本中
                    val currentText = voiceTextInput.text.toString()
                    val newText = if (currentText.isEmpty()) text else "$currentText $text"

                    voiceTextInput.setText(newText)
                    voiceTextInput.setSelection(newText.length)
                    voiceStatusText.text = "识别完成，可以继续语音输入或开始搜索"
                    voiceSearchButton.isEnabled = true

                    // 重置监听状态
                    isListening = false
                    updateVoiceListeningState(false)
                }
            }

            override fun onVoiceInputError(error: String) {
                runOnUiThread {
                    voiceStatusText.text = error
                    isListening = false
                    updateVoiceListeningState(false)
                }
            }

            override fun onVoiceInputCancelled() {
                runOnUiThread {
                    voiceStatusText.text = "语音输入已取消"
                    isListening = false
                    updateVoiceListeningState(false)
                }
            }
        })

        // 检测语音支持情况并更新UI
        detectAndUpdateVoiceSupport()

        // 在设置完所有适配器和监听器后，延迟加载当前设置以确保UI完全初始化
        Handler(Looper.getMainLooper()).post {
            loadSettings()
        }
    }

    /**
     * 检测语音支持情况并更新UI
     */
    private fun detectAndUpdateVoiceSupport() {
        try {
            voiceSupportInfo = voiceInputManager.detectVoiceSupport()
            updateVoiceButtonState(voiceSupportInfo!!)
            Log.d(TAG, "语音支持检测完成: ${voiceSupportInfo!!.statusMessage}")

            // 如果不支持语音输入，显示提示
            if (voiceSupportInfo!!.supportLevel == VoiceInputManager.SupportLevel.NO_SUPPORT) {
                showNoVoiceSupportHint()
            }
        } catch (e: Exception) {
            Log.e(TAG, "语音支持检测失败", e)
            // 如果检测失败，默认为不支持状态
            voiceSupportInfo = VoiceInputManager.VoiceSupportInfo(
                isSupported = false,
                supportLevel = VoiceInputManager.SupportLevel.NO_SUPPORT,
                availableMethods = listOf("手动输入"),
                recommendedMethod = "手动输入",
                statusMessage = "语音功能检测失败"
            )
            updateVoiceButtonState(voiceSupportInfo!!)
            showNoVoiceSupportHint()
        }
    }

    /**
     * 显示不支持语音输入的提示 - 已修改为静默处理
     */
    private fun showNoVoiceSupportHint() {
        // 不再显示Toast提示，静默切换到文本输入模式
        Log.d(TAG, "检测到设备不支持语音输入，已自动切换到文本输入模式")
    }

    /**
     * 根据语音支持情况更新语音按钮状态
     */
    private fun updateVoiceButtonState(supportInfo: VoiceInputManager.VoiceSupportInfo) {
        runOnUiThread {
            if (supportInfo.isSupported) {
                // 支持SpeechRecognizer：显示语音相关UI
                voiceMicContainer.visibility = View.VISIBLE
                voiceMicContainer.isEnabled = true
                voiceStatusText.text = "点击麦克风开始语音输入"
                voiceStatusText.setTextColor(ContextCompat.getColor(this, R.color.simple_mode_text_primary_light))

                // 显示语音提示文本和设置
                voiceHintText?.visibility = View.VISIBLE
                voiceSettingsCard?.visibility = View.VISIBLE

                Log.d(TAG, "显示语音UI：SpeechRecognizer可用")
            } else {
                // 不支持SpeechRecognizer：隐藏语音相关UI，自动显示输入法
                voiceMicContainer.visibility = View.GONE
                voiceStatusText.text = "请直接输入文本"
                voiceStatusText.setTextColor(ContextCompat.getColor(this, R.color.simple_mode_text_secondary_light))

                // 隐藏语音提示文本和设置
                voiceHintText?.visibility = View.GONE
                voiceSettingsCard?.visibility = View.GONE

                // 自动聚焦到文本输入框并显示输入法
                voiceTextInput.requestFocus()
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(voiceTextInput, InputMethodManager.SHOW_IMPLICIT)

                Log.d(TAG, "隐藏语音UI：SpeechRecognizer不可用，自动显示输入法")
            }
        }
    }

    /**
     * 更新左右手模式布局
     */
    private fun updateLayoutForHandedness(isLeftHanded: Boolean) {
        // 应用RTL布局方向
        applyLayoutDirection(isLeftHanded)

        // 更新搜索引擎抽屉位置和按钮
        updateSearchDrawerForHandedness(isLeftHanded)

        // 更新设置页面标题对齐
        updateSettingsPageLayout(isLeftHanded)

        // 更新底部导航栏布局方向
        updateBottomNavigationDirection(isLeftHanded)

        // 不再对整个布局进行镜像翻转，避免影响WebView内容
        // 只更新需要适应左手模式的特定组件

        // 更新四分之一圆弧操作栏的位置和模式
        quarterArcOperationBar?.let { operationBar ->
            // 先更新布局参数
            val layoutParams = operationBar.layoutParams as? FrameLayout.LayoutParams
            layoutParams?.let { params ->
                params.gravity = if (isLeftHanded) {
                    android.view.Gravity.BOTTOM or android.view.Gravity.START
                } else {
                    android.view.Gravity.BOTTOM or android.view.Gravity.END
                }

                val margin = (16 * resources.displayMetrics.density).toInt()
                params.leftMargin = if (isLeftHanded) margin else 0
                params.rightMargin = if (isLeftHanded) 0 else margin
                params.bottomMargin = margin

                operationBar.layoutParams = params

                // 强制重新布局
                operationBar.requestLayout()

                Log.d(TAG, "四分之一圆弧操作栏布局参数已更新为左手模式: $isLeftHanded")
            }

            // 然后设置左手模式（这会触发内部重新计算）
            operationBar.setLeftHandedMode(isLeftHanded)
        }

        // 可以在这里添加其他需要适应左手模式的UI组件
        // 但不影响WebView的内容排列
    }

    /**
     * 应用布局方向（RTL支持）
     */
    private fun applyLayoutDirection(isLeftHanded: Boolean) {
        if (isLeftHanded) {
            window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
        } else {
            window.decorView.layoutDirection = View.LAYOUT_DIRECTION_LTR
        }
    }

    /**
     * 更新搜索引擎抽屉位置以适应左手模式
     */
    private fun updateSearchDrawerForHandedness(isLeftHanded: Boolean) {
        // 查找搜索页面中的抽屉布局
        val drawerLayout = findViewById<androidx.drawerlayout.widget.DrawerLayout>(R.id.browser_layout)
        val drawerContent = findViewById<LinearLayout>(R.id.browser_nav_drawer)

        if (drawerLayout != null && drawerContent != null) {
            // 抽屉在布局文件中固定为START位置，不需要动态修改
            // 只需要确保抽屉是解锁状态
            drawerLayout.setDrawerLockMode(androidx.drawerlayout.widget.DrawerLayout.LOCK_MODE_UNLOCKED, androidx.core.view.GravityCompat.START)
            drawerLayout.setDrawerLockMode(androidx.drawerlayout.widget.DrawerLayout.LOCK_MODE_LOCKED_CLOSED, androidx.core.view.GravityCompat.END)

            Log.d(TAG, "抽屉锁定模式已更新，START: ${drawerLayout.getDrawerLockMode(androidx.core.view.GravityCompat.START)}")
        }

        // 更新标题栏按钮位置
        updateToolbarButtonsForHandedness(isLeftHanded)
    }

    /**
     * 更新标题栏按钮位置以适应左手模式
     */
    private fun updateToolbarButtonsForHandedness(isLeftHanded: Boolean) {
        // 查找搜索页面的标题栏按钮
        val closeButton = findViewById<ImageButton>(R.id.browser_btn_close)
        val menuButton = findViewById<ImageButton>(R.id.browser_btn_menu)
        val searchInput = findViewById<EditText>(R.id.browser_search_input)
        val toolbar = closeButton?.parent as? LinearLayout

        if (toolbar != null && closeButton != null && menuButton != null && searchInput != null) {
            // 确保按钮可见
            closeButton.visibility = View.VISIBLE
            menuButton.visibility = View.VISIBLE
            searchInput.visibility = View.VISIBLE

            // 不重新排列按钮，只是确保它们都可见
            // 让RTL布局自动处理视觉位置

            // 设置菜单按钮的点击事件来打开抽屉
            menuButton.setOnClickListener {
                val drawerLayout = findViewById<androidx.drawerlayout.widget.DrawerLayout>(R.id.browser_layout)
                if (drawerLayout != null) {
                    // 抽屉在布局中固定为START位置，所以统一使用START
                    if (drawerLayout.isDrawerOpen(androidx.core.view.GravityCompat.START)) {
                        Log.d(TAG, "关闭抽屉")
                        drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START)
                    } else {
                        Log.d(TAG, "打开抽屉")
                        drawerLayout.openDrawer(androidx.core.view.GravityCompat.START)
                    }
                } else {
                    Log.e(TAG, "找不到浏览器抽屉布局")
                }
            }
        }
    }

    /**
     * 更新底部导航栏布局方向
     */
    private fun updateBottomNavigationDirection(isLeftHanded: Boolean) {
        try {
            val bottomNav = findViewById<LinearLayout>(R.id.bottom_navigation)
            if (bottomNav != null) {
                // 无论左手还是右手模式，都保持LTR方向，确保tab顺序一致
                bottomNav.layoutDirection = View.LAYOUT_DIRECTION_LTR
                Log.d(TAG, "底部导航栏布局方向已设置为LTR，保持tab顺序一致")
            }
        } catch (e: Exception) {
            Log.e(TAG, "更新底部导航栏布局方向时出错", e)
        }
    }













    /**
     * 更新设置页面布局以适应左手模式
     */
    private fun updateSettingsPageLayout(isLeftHanded: Boolean) {
        try {
            Log.d(TAG, "更新设置页面布局，左手模式: $isLeftHanded")

            // 查找设置页面的主要标题元素
            val settingsLayout = findViewById<ScrollView>(R.id.settings_layout)
            if (settingsLayout == null) {
                Log.d(TAG, "设置页面布局未找到")
                return
            }

            // 查找并更新所有设置标题的对齐方式
            updateSettingsTitleAlignment(settingsLayout, isLeftHanded)

        } catch (e: Exception) {
            Log.e(TAG, "更新设置页面布局时出错", e)
        }
    }

    /**
     * 更新设置标题的对齐方式
     */
    private fun updateSettingsTitleAlignment(parentView: View, isLeftHanded: Boolean) {
        try {
            when (parentView) {
                is ViewGroup -> {
                    // 检查当前ViewGroup是否是标题布局
                    if (parentView is LinearLayout && parentView.orientation == LinearLayout.HORIZONTAL) {
                        // 检查是否包含ImageView和TextView（标题的典型结构）
                        var hasImageView = false
                        var hasTextView = false
                        var textView: TextView? = null

                        for (i in 0 until parentView.childCount) {
                            val child = parentView.getChildAt(i)
                            when (child) {
                                is ImageView -> hasImageView = true
                                is TextView -> {
                                    hasTextView = true
                                    textView = child
                                }
                            }
                        }

                        // 如果是标题布局，更新对齐方式
                        if (hasImageView && hasTextView && textView != null) {
                            // 检查文字大小，确保是标题而不是普通文本
                            val textSizeInSp = textView.textSize / resources.displayMetrics.scaledDensity
                            if (textSizeInSp >= 16f) { // 标题通常是16sp或更大
                                if (isLeftHanded) {
                                    // 左手模式：标题靠右对齐
                                    parentView.gravity = android.view.Gravity.END or android.view.Gravity.CENTER_VERTICAL
                                    Log.d(TAG, "设置标题右对齐: ${textView.text}")
                                } else {
                                    // 右手模式：标题靠左对齐
                                    parentView.gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
                                    Log.d(TAG, "设置标题左对齐: ${textView.text}")
                                }
                            }
                        }
                    }

                    // 递归处理子视图
                    for (i in 0 until parentView.childCount) {
                        updateSettingsTitleAlignment(parentView.getChildAt(i), isLeftHanded)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "更新标题对齐时出错", e)
        }
    }

    /**
     * 递归查找所有TextView
     */
    private fun findAllTextViews(view: View, textViews: ArrayList<TextView>) {
        if (view is TextView) {
            textViews.add(view)
        } else if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findAllTextViews(view.getChildAt(i), textViews)
            }
        }
    }

    private fun loadSettings() {
        try {
            // 加载显示模式
            val currentDisplayMode = settingsManager.getDisplayMode()
            val displayModeValues = resources.getStringArray(R.array.display_mode_values)
            val displayModeEntries = resources.getStringArray(R.array.display_mode_entries)
            val displayModeIndex = displayModeValues.indexOf(currentDisplayMode)

            Log.d(TAG, "当前显示模式: $currentDisplayMode")
            Log.d(TAG, "显示模式值数组: ${displayModeValues.joinToString(", ")}")
            Log.d(TAG, "显示模式条目数组: ${displayModeEntries.joinToString(", ")}")
            Log.d(TAG, "显示模式索引: $displayModeIndex")

            if (displayModeIndex != -1) {
                displayModeSpinner.setSelection(displayModeIndex, false)
                Log.d(TAG, "设置显示模式Spinner选择: $displayModeIndex (${displayModeEntries.getOrNull(displayModeIndex)})")

                // 验证设置是否成功，如果失败则强制刷新
                displayModeSpinner.post {
                    val actualSelection = displayModeSpinner.selectedItemPosition
                    Log.d(TAG, "显示模式Spinner实际选择: $actualSelection")
                    if (actualSelection != displayModeIndex) {
                        Log.w(TAG, "显示模式Spinner选择失败，尝试强制设置")
                        displayModeSpinner.setSelection(displayModeIndex, true)
                    }
                }
            } else {
                // 数据被旧版本写坏或缺失时，回退到第一个选项并写回
                displayModeSpinner.setSelection(0, false)
                if (displayModeValues.isNotEmpty()) {
                    settingsManager.setDisplayMode(displayModeValues[0])
                }
                Log.d(TAG, "显示模式索引无效，回退到第一个选项")
            }

            // 加载主题设置
            val themeMode = settingsManager.getThemeMode()
            val themeEntries = resources.getStringArray(R.array.theme_mode_entries)
            Log.d(TAG, "当前主题模式: $themeMode")
            Log.d(TAG, "主题模式条目数组: ${themeEntries.joinToString(", ")}")

            // 修复主题模式加载映射：
            // 常量值 → 数组索引：
            // THEME_MODE_SYSTEM(-1) → 0 (跟随系统)
            // THEME_MODE_LIGHT(0) → 1 (浅色模式)
            // THEME_MODE_DARK(1) → 2 (深色模式)
            val themeIndex = when(themeMode) {
                SettingsManager.THEME_MODE_SYSTEM -> 0  // 跟随系统
                SettingsManager.THEME_MODE_LIGHT -> 1   // 浅色模式
                SettingsManager.THEME_MODE_DARK -> 2    // 深色模式
                else -> 0 // 默认跟随系统
            }

            Log.d(TAG, "设置主题模式Spinner选择: $themeIndex (${themeEntries.getOrNull(themeIndex)})")
            themeModeSpinner.setSelection(themeIndex, false)

            // 验证设置是否成功，如果失败则强制刷新
            themeModeSpinner.post {
                val actualSelection = themeModeSpinner.selectedItemPosition
                Log.d(TAG, "主题模式Spinner实际选择: $actualSelection")
                if (actualSelection != themeIndex) {
                    Log.w(TAG, "主题模式Spinner选择失败，尝试强制设置")
                    themeModeSpinner.setSelection(themeIndex, true)
                }
            }

            // 加载开关状态
            clipboardMonitorSwitch.isChecked = settingsManager.isClipboardListenerEnabled()
            autoHideSwitch.isChecked = settingsManager.getAutoHide()
            aiModeSwitch.isChecked = settingsManager.getIsAIMode()
            leftHandedSwitch.isChecked = settingsManager.isLeftHandedModeEnabled()
            autoPasteSwitch.isChecked = settingsManager.isAutoPasteEnabled()
            multiTabBrowserSwitch.isChecked = settingsManager.getBoolean("use_multi_tab_browser", false)
            notificationListenerSwitch.isChecked = settingsManager.getBoolean("enable_notification_listener", false)

            // 加载透明度设置 - getBallAlpha()返回0-255，需要转换为0-100
            ballAlphaSeekbar.progress = ((settingsManager.getBallAlpha() / 255.0) * 100).toInt()



            // 加载窗口数量设置
            val windowCount = settingsManager.getDefaultWindowCount().coerceIn(1, resources.getStringArray(R.array.window_count_entries).size)
            windowCountSpinner.setSelection(windowCount - 1) // 转换为0-based索引

            // 立即应用左手模式
            val isLeftHanded = settingsManager.isLeftHandedModeEnabled()
            updateLayoutForHandedness(isLeftHanded)
            applyLayoutDirection(isLeftHanded)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading settings", e)
            Toast.makeText(this, "加载设置时出错", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showVoice() {
        currentState = UIState.VOICE
        chatLayout.visibility = View.GONE
        taskSelectionLayout.visibility = View.GONE
        stepGuidanceLayout.visibility = View.GONE
        promptPreviewLayout.visibility = View.GONE
        voiceLayout.visibility = View.VISIBLE
        appSearchLayout.visibility = View.GONE
        browserLayout.visibility = View.GONE
        settingsLayout.visibility = View.GONE

        // 检查录音权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.RECORD_AUDIO), 1001)
        }

        updateTabColors()
    }

    private fun showAppSearch() {
        currentState = UIState.APP_SEARCH
        chatLayout.visibility = View.GONE
        taskSelectionLayout.visibility = View.GONE
        stepGuidanceLayout.visibility = View.GONE
        promptPreviewLayout.visibility = View.GONE
        voiceLayout.visibility = View.GONE
        appSearchLayout.visibility = View.VISIBLE
        browserLayout.visibility = View.GONE
        settingsLayout.visibility = View.GONE

        // 初始化应用搜索页面
        setupAppSearchPage()

        updateTabColors()
    }

    /**
     * 设置应用搜索页面
     */
    private fun setupAppSearchPage() {
        Log.d(TAG, "setupAppSearchPage被调用")
        Log.d(TAG, "appSearchAdapter是否已初始化: ${::appSearchAdapter.isInitialized}")
        // 初始化应用搜索适配器
        if (!::appSearchAdapter.isInitialized) {
            Log.d(TAG, "开始初始化appSearchAdapter")
            appSearchAdapter = AppSearchGridAdapter(this, currentAppConfigs,
                onAppClick = { appConfig, query ->
                    handleAppSearch(appConfig, query)
                },
                onAppSelected = { appConfig ->
                    updateSelectedAppDisplay(appConfig)
                },
                getCurrentQuery = {
                    appSearchInput.text.toString()
                }
            )

            // 设置网格布局 - 采用launcher风格的4列布局
            val gridLayoutManager = GridLayoutManager(this, 4) // 4列网格，类似标准launcher
            appSearchGrid.layoutManager = gridLayoutManager
            appSearchGrid.adapter = appSearchAdapter

            // 设置分类点击事件
            setupCategoryClickListeners()

            // 设置搜索输入框
            setupAppSearchInput()

            // 加载默认分类的应用
            loadAppsByCategory(AppCategory.ALL)
        }
    }

    /**
     * 设置分类点击事件
     */
    private fun setupCategoryClickListeners() {
        // 获取所有分类按钮
        val categoryButtons = mapOf(
            AppCategory.CUSTOM to findViewById<LinearLayout>(R.id.category_custom),
            AppCategory.ALL to findViewById<LinearLayout>(R.id.category_all),
            AppCategory.AI to findViewById<LinearLayout>(R.id.category_ai),
            AppCategory.SHOPPING to findViewById<LinearLayout>(R.id.category_shopping),
            AppCategory.SOCIAL to findViewById<LinearLayout>(R.id.category_social),
            AppCategory.VIDEO to findViewById<LinearLayout>(R.id.category_video),
            AppCategory.MUSIC to findViewById<LinearLayout>(R.id.category_music),
            AppCategory.LIFESTYLE to findViewById<LinearLayout>(R.id.category_lifestyle),
            AppCategory.MAPS to findViewById<LinearLayout>(R.id.category_maps),
            AppCategory.BROWSER to findViewById<LinearLayout>(R.id.category_browser),
            AppCategory.FINANCE to findViewById<LinearLayout>(R.id.category_finance),
            AppCategory.TRAVEL to findViewById<LinearLayout>(R.id.category_travel),
            AppCategory.JOBS to findViewById<LinearLayout>(R.id.category_jobs),
            AppCategory.EDUCATION to findViewById<LinearLayout>(R.id.category_education),
            AppCategory.NEWS to findViewById<LinearLayout>(R.id.category_news)
        )

        // 调试：检查AI分类按钮是否找到
        val aiButton = categoryButtons[AppCategory.AI]
        Log.d(TAG, "AI分类按钮是否找到: ${aiButton != null}")
        if (aiButton != null) {
            Log.d(TAG, "AI分类按钮ID: ${aiButton.id}")
            Log.d(TAG, "AI分类按钮可见性: ${aiButton.visibility}")
        }

        // 设置分类按钮的图标和文字
        setupCategoryButtonContent(categoryButtons)

        // 根据保存的排序重新排列按钮
        val categoryOrder = categoryDragHelper.getCategoryOrder()
        categoryDragHelper.reorderCategoryButtons(appCategorySidebar, categoryButtons, categoryOrder)

        // 设置点击事件
        categoryButtons.forEach { (category, button) ->
            button?.setOnClickListener {
                selectAppCategory(category, categoryButtons)
            }
        }

        // 设置拖拽功能
        categoryDragHelper.setupDragListeners(appCategorySidebar, categoryButtons) { newOrder ->
            // 拖拽排序改变后的回调
            Toast.makeText(this, "分类排序已更新", Toast.LENGTH_SHORT).show()
        }

        // 默认选中"全部"分类
        selectAppCategory(AppCategory.ALL, categoryButtons)
    }

    /**
     * 设置分类按钮的图标和文字内容
     */
    private fun setupCategoryButtonContent(categoryButtons: Map<AppCategory, LinearLayout?>) {
        val categoryData = mapOf(
            AppCategory.CUSTOM to Pair(R.drawable.ic_star, "自定义"),
            AppCategory.ALL to Pair(R.drawable.ic_apps, "全部"),
            AppCategory.AI to Pair(R.drawable.ic_ai, "AI"),
            AppCategory.SHOPPING to Pair(R.drawable.ic_shopping, "购物"),
            AppCategory.SOCIAL to Pair(R.drawable.ic_people, "社交"),
            AppCategory.VIDEO to Pair(R.drawable.ic_video, "视频"),
            AppCategory.MUSIC to Pair(R.drawable.ic_music, "音乐"),
            AppCategory.LIFESTYLE to Pair(R.drawable.ic_home, "生活"),
            AppCategory.MAPS to Pair(R.drawable.ic_map, "地图"),
            AppCategory.BROWSER to Pair(R.drawable.ic_web, "浏览器"),
            AppCategory.FINANCE to Pair(R.drawable.ic_payment, "金融"),
            AppCategory.TRAVEL to Pair(R.drawable.ic_directions_car, "出行"),
            AppCategory.JOBS to Pair(R.drawable.ic_work, "招聘"),
            AppCategory.EDUCATION to Pair(R.drawable.ic_school, "教育"),
            AppCategory.NEWS to Pair(R.drawable.ic_article, "新闻")
        )

        categoryButtons.forEach { (category, button) ->
            button?.let { layout ->
                val data = categoryData[category]
                if (data != null) {
                    val iconView = layout.findViewById<ImageView>(R.id.category_icon)
                    val textView = layout.findViewById<TextView>(R.id.category_text)

                    iconView?.setImageResource(data.first)
                    textView?.text = data.second
                }
            }
        }
    }

    /**
     * 选择应用分类
     */
    private fun selectAppCategory(category: AppCategory, categoryButtons: Map<AppCategory, LinearLayout?>) {
        currentAppCategory = category

        // 更新分类按钮的选中状态
        categoryButtons.forEach { (cat, button) ->
            button?.let { layout ->
                if (cat == category) {
                    // 选中状态：使用Material Design选中样式
                    layout.setBackgroundResource(R.drawable.material_category_button_selected)

                    // 更新图标和文字颜色为选中状态
                    val iconView = layout.findViewById<ImageView>(R.id.category_icon)
                    val textView = layout.findViewById<TextView>(R.id.category_text)

                    iconView?.setColorFilter(ContextCompat.getColor(this, R.color.simple_mode_accent_light))
                    textView?.setTextColor(ContextCompat.getColor(this, R.color.simple_mode_accent_light))
                } else {
                    // 未选中状态：透明背景
                    layout.setBackgroundResource(R.drawable.material_category_button_background)

                    // 更新图标和文字颜色为未选中状态
                    val iconView = layout.findViewById<ImageView>(R.id.category_icon)
                    val textView = layout.findViewById<TextView>(R.id.category_text)

                    iconView?.setColorFilter(ContextCompat.getColor(this, R.color.simple_mode_accent_light))
                    textView?.setTextColor(ContextCompat.getColor(this, R.color.simple_mode_text_primary_light))
                }
            }
        }

        // 加载对应分类的应用
        loadAppsByCategory(category)
    }

    /**
     * 根据分类加载应用
     */
    private fun loadAppsByCategory(category: AppCategory) {
        currentAppConfigs.clear()
        currentAppConfigs.addAll(appSearchSettings.getAppConfigsByCategory(category))
        appSearchAdapter.updateAppConfigs(currentAppConfigs)

        // 重置搜索框图标为默认状态
        resetSearchInputIcon()
    }

    /**
     * 设置应用搜索输入框
     */
    private fun setupAppSearchInput() {
        val textInputLayout = findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.app_search_input_layout)

        // 设置搜索动作监听
        appSearchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = appSearchInput.text.toString().trim()
                if (query.isNotEmpty()) {
                    appSearchAdapter.updateSearchQuery(query)
                    appSearchHint.text = "输入关键词：$query，点击应用图标进行搜索"
                    // 显示清空按钮
                    updateInputLayoutEndIcon(true)
                } else {
                    appSearchAdapter.updateSearchQuery("")
                    appSearchHint.text = "选择${currentAppCategory.displayName}应用进行搜索"
                    // 显示历史按钮
                    updateInputLayoutEndIcon(false)
                }
                true
            } else {
                false
            }
        }

        // 设置文本变化监听
        appSearchInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val hasText = !s.isNullOrEmpty()
                updateInputLayoutEndIcon(hasText)
            }
        })

        // 设置结束图标点击监听
        textInputLayout?.setEndIconOnClickListener {
            val query = appSearchInput.text.toString().trim()
            if (query.isNotEmpty()) {
                // 清空输入框
                appSearchInput.text?.clear()
                appSearchAdapter.updateSearchQuery("")
                resetSearchInputIcon()
            } else {
                // 显示搜索历史
                showSearchHistory()
            }
        }

        // 设置开始图标点击监听（放大镜图标）
        textInputLayout?.setStartIconOnClickListener {
            showAppSelectionHistory()
        }
    }

    /**
     * 更新选中应用显示
     */
    private fun updateSelectedAppDisplay(appConfig: AppSearchConfig) {
        try {
            Log.d(TAG, "开始更新选中应用显示: ${appConfig.appName}, 包名: ${appConfig.packageName}")

            // 在主线程中执行UI更新
            runOnUiThread {
                try {
                    // 获取应用图标
                    val drawable = getAppIconDrawable(appConfig)

                    if (drawable != null) {
                        // 设置应用图标
                        selectedAppIcon.setImageDrawable(drawable)

                        // 显示图标容器
                        selectedAppIconContainer.visibility = View.VISIBLE

                        // 添加点击事件，点击图标可以快速搜索
                        selectedAppIconContainer.setOnClickListener {
                            val query = appSearchInput.text.toString().trim()
                            if (query.isNotEmpty()) {
                                handleAppSearch(appConfig, query)
                            } else {
                                Toast.makeText(this@SimpleModeActivity, "请输入搜索关键词", Toast.LENGTH_SHORT).show()
                            }
                        }

                        Log.d(TAG, "成功设置选中应用图标")

                        // 保存到选择历史
                        appSelectionHistoryManager.addAppSelection(appConfig)

                        // 更新提示文本
                        appSearchHint.text = "已选择 ${appConfig.appName}，输入关键词进行搜索"
                    } else {
                        Log.w(TAG, "无法获取应用图标")
                        hideSelectedAppDisplay()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "在UI线程中更新选中应用显示失败", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "更新选中应用显示失败", e)
        }
    }

    /**
     * 隐藏选中应用显示
     */
    private fun hideSelectedAppDisplay() {
        selectedAppIconContainer.visibility = View.GONE
        appSearchHint.text = "选择全部应用进行搜索"
    }

    /**
     * 显示应用选择历史
     */
    private fun showAppSelectionHistory() {
        try {
            val recentApps = appSelectionHistoryManager.getRecentApps()

            if (recentApps.isEmpty()) {
                Toast.makeText(this, "暂无应用选择历史", Toast.LENGTH_SHORT).show()
                return
            }

            // 创建自定义对话框显示应用图标网格
            showAppSelectionDialog(recentApps)

        } catch (e: Exception) {
            Log.e(TAG, "显示应用选择历史失败", e)
            Toast.makeText(this, "加载历史记录失败", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 显示应用选择对话框
     */
    private fun showAppSelectionDialog(recentApps: List<AppSelectionHistoryManager.AppSelectionItem>) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_app_selection, null)
        val gridView = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.app_selection_grid)

        // 设置网格布局
        gridView.layoutManager = androidx.recyclerview.widget.GridLayoutManager(this, 4)

        // 创建适配器
        val adapter = AppSelectionDialogAdapter(this, recentApps) { selectionItem: AppSelectionHistoryManager.AppSelectionItem ->
            // 点击应用时的回调
            selectAppFromHistory(selectionItem)
        }
        gridView.adapter = adapter

        // 创建对话框
        AlertDialog.Builder(this)
            .setTitle("最近选择的应用")
            .setView(dialogView)
            .setNegativeButton("清空历史") { _, _ ->
                AlertDialog.Builder(this)
                    .setTitle("确认清空")
                    .setMessage("确定要清空应用选择历史吗？")
                    .setPositiveButton("确定") { _, _ ->
                        appSelectionHistoryManager.clearSelectionHistory()
                        Toast.makeText(this, "历史记录已清空", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
            .setNeutralButton("取消", null)
            .show()
    }

    /**
     * 从历史记录中选择应用
     */
    private fun selectAppFromHistory(selectionItem: AppSelectionHistoryManager.AppSelectionItem) {
        // 找到对应的应用配置
        val appConfig = currentAppConfigs.find { it.packageName == selectionItem.packageName }

        if (appConfig != null) {
            // 更新选中应用显示
            updateSelectedAppDisplay(appConfig)
            Toast.makeText(this, "已选择 ${appConfig.appName}", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "应用 ${selectionItem.appName} 不可用", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 获取应用图标Drawable
     */
    private fun getAppIconDrawable(appConfig: AppSearchConfig): android.graphics.drawable.Drawable? {
        return try {
            if (isAppInstalled(appConfig.packageName)) {
                Log.d(TAG, "应用已安装，获取真实图标")
                packageManager.getApplicationIcon(appConfig.packageName)
            } else {
                Log.d(TAG, "应用未安装，使用分类图标: ${appConfig.category.iconResId}")
                ContextCompat.getDrawable(this, appConfig.category.iconResId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取应用图标失败", e)
            ContextCompat.getDrawable(this, R.drawable.ic_search)
        }
    }

    /**
     * 创建缩放后的图标
     */
    private fun createScaledIcon(originalDrawable: android.graphics.drawable.Drawable): android.graphics.drawable.Drawable {
        return try {
            val size = resources.getDimensionPixelSize(R.dimen.search_input_icon_size_small) // 20dp

            // 创建bitmap
            val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)

            // 设置drawable的边界并绘制
            originalDrawable.setBounds(0, 0, size, size)
            originalDrawable.draw(canvas)

            // 创建BitmapDrawable
            android.graphics.drawable.BitmapDrawable(resources, bitmap)
        } catch (e: Exception) {
            Log.e(TAG, "创建缩放图标失败，返回原图标", e)
            originalDrawable
        }
    }

    /**
     * 重置搜索输入框图标为默认状态
     */
    private fun resetSearchInputIcon() {
        hideSelectedAppDisplay()
        appSearchHint.text = "选择${currentAppCategory.displayName}应用进行搜索"
    }

    /**
     * 更新输入框结束图标
     */
    private fun updateInputLayoutEndIcon(hasText: Boolean) {
        try {
            val textInputLayout = findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.app_search_input_layout)

            if (textInputLayout != null) {
                if (hasText) {
                    // 显示清空图标
                    textInputLayout.endIconDrawable = ContextCompat.getDrawable(this, R.drawable.ic_clear)
                } else {
                    // 显示历史图标
                    textInputLayout.endIconDrawable = ContextCompat.getDrawable(this, R.drawable.ic_history)
                }
            }
        } catch (e: Exception) {
            Log.e("SimpleModeActivity", "更新输入框结束图标失败", e)
        }
    }

    /**
     * 显示搜索历史
     */
    private fun showSearchHistory() {
        try {
            val historyList = searchHistoryManager.getSearchHistory()
            if (historyList.isEmpty()) {
                Toast.makeText(this, "暂无搜索历史", Toast.LENGTH_SHORT).show()
                return
            }

            val historyItems = historyList.map { "${it.query} (${it.appName})" }.toTypedArray()

            AlertDialog.Builder(this)
                .setTitle("搜索历史")
                .setItems(historyItems) { _, which ->
                    val selectedHistory = historyList[which]
                    // 填充到输入框
                    appSearchInput.setText(selectedHistory.query)
                    appSearchInput.setSelection(selectedHistory.query.length)

                    // 更新搜索查询
                    appSearchAdapter.updateSearchQuery(selectedHistory.query)
                    appSearchHint.text = "输入关键词：${selectedHistory.query}，点击应用图标进行搜索"
                    updateInputLayoutEndIcon(true)
                }
                .setNegativeButton("清空历史") { _, _ ->
                    AlertDialog.Builder(this)
                        .setTitle("确认清空")
                        .setMessage("确定要清空所有搜索历史吗？")
                        .setPositiveButton("确定") { _, _ ->
                            searchHistoryManager.clearSearchHistory()
                            Toast.makeText(this, "搜索历史已清空", Toast.LENGTH_SHORT).show()
                        }
                        .setNegativeButton("取消", null)
                        .show()
                }
                .setNeutralButton("取消", null)
                .show()

        } catch (e: Exception) {
            Log.e("SimpleModeActivity", "显示搜索历史失败", e)
        }
    }

    /**
     * 显示搜索历史并支持设置默认选项
     */
    private fun showSearchHistoryWithDefault() {
        try {
            val historyList = searchHistoryManager.getSearchHistory()
            val defaultSearch = searchHistoryManager.getDefaultSearch()

            if (historyList.isEmpty() && defaultSearch == null) {
                Toast.makeText(this, "暂无搜索历史", Toast.LENGTH_SHORT).show()
                return
            }

            val items = mutableListOf<String>()
            val actions = mutableListOf<() -> Unit>()

            // 添加默认搜索选项
            defaultSearch?.let { (appName, _) ->
                items.add("🌟 默认搜索：$appName")
                actions.add {
                    // 执行默认搜索
                    executeDefaultSearch()
                }
            }

            // 添加历史记录
            historyList.forEach { history ->
                val isDefault = defaultSearch?.second == history.packageName
                val prefix = if (isDefault) "⭐ " else ""
                items.add("$prefix${history.query} (${history.appName})")
                actions.add {
                    // 填充到输入框
                    appSearchInput.setText(history.query)
                    appSearchInput.setSelection(history.query.length)

                    // 更新搜索查询
                    appSearchAdapter.updateSearchQuery(history.query)
                    appSearchHint.text = "输入关键词：${history.query}，点击应用图标进行搜索"
                    updateInputLayoutEndIcon(true)
                }
            }

            AlertDialog.Builder(this)
                .setTitle("搜索选项")
                .setItems(items.toTypedArray()) { _, which ->
                    actions[which].invoke()
                }
                .setNegativeButton("管理") { _, _ ->
                    showSearchManagement()
                }
                .setNeutralButton("取消", null)
                .show()

        } catch (e: Exception) {
            Log.e("SimpleModeActivity", "显示搜索选项失败", e)
        }
    }

    /**
     * 执行默认搜索
     */
    private fun executeDefaultSearch() {
        val defaultSearch = searchHistoryManager.getDefaultSearch() ?: return
        val (appName, packageName) = defaultSearch

        // 找到对应的应用配置
        val appConfig = currentAppConfigs.find { it.packageName == packageName }
        if (appConfig != null) {
            val query = appSearchInput.text.toString().trim()
            if (query.isNotEmpty()) {
                handleAppSearch(appConfig, query)
            } else {
                // 更新选中应用显示
                updateSelectedAppDisplay(appConfig)
                Toast.makeText(this, "已选择默认搜索：$appName", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "默认搜索应用不可用", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 显示搜索管理界面
     */
    private fun showSearchManagement() {
        val historyList = searchHistoryManager.getSearchHistory()
        val defaultSearch = searchHistoryManager.getDefaultSearch()

        if (historyList.isEmpty()) {
            Toast.makeText(this, "暂无搜索历史", Toast.LENGTH_SHORT).show()
            return
        }

        val items = historyList.map { history ->
            val isDefault = defaultSearch?.second == history.packageName
            val prefix = if (isDefault) "⭐ " else ""
            "$prefix${history.query} (${history.appName})"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("搜索管理")
            .setItems(items) { _, which ->
                val selectedHistory = historyList[which]
                showHistoryItemMenu(selectedHistory)
            }
            .setNegativeButton("清空所有") { _, _ ->
                AlertDialog.Builder(this)
                    .setTitle("确认清空")
                    .setMessage("确定要清空所有搜索历史吗？")
                    .setPositiveButton("确定") { _, _ ->
                        searchHistoryManager.clearSearchHistory()
                        searchHistoryManager.clearDefaultSearch()
                        Toast.makeText(this, "搜索历史已清空", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
            .setNeutralButton("取消", null)
            .show()
    }

    /**
     * 显示历史项目菜单
     */
    private fun showHistoryItemMenu(historyItem: SearchHistoryManager.SearchHistoryItem) {
        val defaultSearch = searchHistoryManager.getDefaultSearch()
        val isDefault = defaultSearch?.second == historyItem.packageName

        val options = if (isDefault) {
            arrayOf("使用此搜索", "取消默认设置", "删除此项")
        } else {
            arrayOf("使用此搜索", "设为默认", "删除此项")
        }

        AlertDialog.Builder(this)
            .setTitle("${historyItem.query} (${historyItem.appName})")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        // 使用此搜索
                        appSearchInput.setText(historyItem.query)
                        appSearchInput.setSelection(historyItem.query.length)
                        appSearchAdapter.updateSearchQuery(historyItem.query)
                        appSearchHint.text = "输入关键词：${historyItem.query}，点击应用图标进行搜索"
                        updateInputLayoutEndIcon(true)
                    }
                    1 -> {
                        if (isDefault) {
                            // 取消默认设置
                            searchHistoryManager.clearDefaultSearch()
                            Toast.makeText(this, "已取消默认设置", Toast.LENGTH_SHORT).show()
                        } else {
                            // 设为默认
                            searchHistoryManager.setDefaultSearch(historyItem.appName, historyItem.packageName)
                            Toast.makeText(this, "已设为默认搜索", Toast.LENGTH_SHORT).show()
                        }
                    }
                    2 -> {
                        // 删除此项
                        searchHistoryManager.removeSearchHistory(historyItem.query, historyItem.packageName)
                        Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 检查应用是否已安装
     */
    private fun isAppInstalled(packageName: String): Boolean {
        return try {
            packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }

        // 实时更新搜索关键词
        appSearchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val query = s?.toString()?.trim() ?: ""
                appSearchAdapter.updateSearchQuery(query)
                if (query.isNotEmpty()) {
                    appSearchHint.text = "输入关键词：$query，点击应用图标进行搜索"
                } else {
                    appSearchHint.text = "选择${currentAppCategory.displayName}应用进行搜索"
                }
            }
        })
    }

    /**
     * 处理应用搜索
     */
    private fun handleAppSearch(appConfig: AppSearchConfig, query: String) {
        try {
            if (query.isNotEmpty()) {
                // 有搜索内容时，使用应用内搜索
                val searchUrl = appConfig.getSearchUrl(query)
                val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(searchUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    setPackage(appConfig.packageName)
                }

                startActivity(intent)
                Toast.makeText(this, "正在打开${appConfig.appName}搜索：$query", Toast.LENGTH_SHORT).show()

                // 保存搜索历史
                searchHistoryManager.addSearchHistory(query, appConfig.appName, appConfig.packageName)
            } else {
                // 没有搜索内容时，直接启动应用
                val launchIntent = packageManager.getLaunchIntentForPackage(appConfig.packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(launchIntent)
                    Toast.makeText(this, "正在启动${appConfig.appName}", Toast.LENGTH_SHORT).show()
                    Log.d(TAG, "直接启动应用: ${appConfig.appName}")
                } else {
                    Log.w(TAG, "无法启动应用: ${appConfig.appName}, 应用可能未安装")
                    Toast.makeText(this, "启动${appConfig.appName}失败，请检查应用是否已安装", Toast.LENGTH_SHORT).show()
                    return
                }
            }

            // 显示悬浮返回按钮
            showFloatingBackButton()

        } catch (e: Exception) {
            Log.e(TAG, "启动应用失败: ${e.message}")
            // 尝试直接启动应用作为备用方案
            try {
                val launchIntent = packageManager.getLaunchIntentForPackage(appConfig.packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(launchIntent)
                    Toast.makeText(this, "正在启动${appConfig.appName}", Toast.LENGTH_SHORT).show()
                    Log.d(TAG, "备用方案启动应用成功: ${appConfig.appName}")
                } else {
                    Toast.makeText(this, "启动${appConfig.appName}失败，请检查应用是否已安装", Toast.LENGTH_SHORT).show()
                }
            } catch (fallbackException: Exception) {
                Log.e(TAG, "备用启动方案也失败: ${fallbackException.message}")
                Toast.makeText(this, "启动${appConfig.appName}失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 显示悬浮返回按钮
     */
    private fun showFloatingBackButton() {
        try {
            // 检查悬浮窗权限
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M && !android.provider.Settings.canDrawOverlays(this)) {
                Log.w(TAG, "没有悬浮窗权限，无法显示返回按钮")
                return
            }

            // 启动悬浮返回按钮服务
            val intent = Intent(this, FloatingBackButtonService::class.java)
            startService(intent)

        } catch (e: Exception) {
            Log.e(TAG, "显示悬浮返回按钮失败", e)
        }
    }

    private fun showSettings() {
        currentState = UIState.SETTINGS
        chatLayout.visibility = View.GONE
        taskSelectionLayout.visibility = View.GONE
        stepGuidanceLayout.visibility = View.GONE
        promptPreviewLayout.visibility = View.GONE
        voiceLayout.visibility = View.GONE
        appSearchLayout.visibility = View.GONE
        browserLayout.visibility = View.GONE
        settingsLayout.visibility = View.VISIBLE

        loadSettings() // 每次显示时重新加载设置
        updateFloatingBallSettingsVisibility() // 根据显示模式控制悬浮球设置可见性
        updateTabColors()
    }

    /**
     * 根据当前显示模式控制悬浮球设置的可见性
     */
    private fun updateFloatingBallSettingsVisibility() {
        try {
            val currentDisplayMode = settingsManager.getDisplayMode()
            val shouldShowFloatingBallSettings = currentDisplayMode == "floating_ball"

            Log.d(TAG, "Current display mode: $currentDisplayMode, show floating ball settings: $shouldShowFloatingBallSettings")

            floatingBallSettingsContainer.visibility = if (shouldShowFloatingBallSettings) {
                View.VISIBLE
            } else {
                View.GONE
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating floating ball settings visibility", e)
            // 默认隐藏悬浮球设置
            floatingBallSettingsContainer.visibility = View.GONE
        }
    }

    private fun showBrowser() {
        currentState = UIState.BROWSER
        chatLayout.visibility = View.GONE
        taskSelectionLayout.visibility = View.GONE
        stepGuidanceLayout.visibility = View.GONE
        promptPreviewLayout.visibility = View.GONE
        voiceLayout.visibility = View.GONE
        appSearchLayout.visibility = View.GONE
        settingsLayout.visibility = View.GONE
        browserLayout.visibility = View.VISIBLE

        // 确保手势卡片式WebView管理器已正确初始化
        if (gestureCardWebViewManager == null) {
            setupBrowserWebView()
        }

        // 检查是否有保存的卡片状态并弹出提示
        checkAndPromptForSavedCards()

        updateTabColors()
    }

    /**
     * 检查保存的卡片状态并弹出加载提示
     * 只在搜索tab中显示恢复提示对话框，且每个会话只显示一次
     */
    private fun checkAndPromptForSavedCards() {
        // 只在搜索tab（UIState.BROWSER）中显示恢复提示
        if (currentState != UIState.BROWSER) {
            Log.d(TAG, "checkAndPromptForSavedCards: 当前不在搜索tab，跳过恢复提示，当前状态: $currentState")
            forceRefreshUIState()
            return
        }
        
        // 如果本次会话已经显示过恢复对话框，则不再显示
        if (hasShownRestoreDialog) {
            Log.d(TAG, "checkAndPromptForSavedCards: 本次会话已显示过恢复对话框，跳过")
            forceRefreshUIState()
            return
        }
        
        val sharedPreferences = getSharedPreferences("gesture_cards_state", Context.MODE_PRIVATE)
        val savedUrls = sharedPreferences.getStringSet("floating_card_urls", emptySet()) ?: emptySet()
        
        Log.d(TAG, "checkAndPromptForSavedCards: 在搜索tab中检查保存的卡片状态")
        Log.d(TAG, "checkAndPromptForSavedCards: savedUrls = $savedUrls")
        Log.d(TAG, "checkAndPromptForSavedCards: savedUrls.size = ${savedUrls.size}")
        
        if (savedUrls.isNotEmpty()) {
            Log.d(TAG, "checkAndPromptForSavedCards: 发现保存的卡片，显示恢复对话框")
            // 标记已显示恢复对话框
            hasShownRestoreDialog = true
            
            // 弹出对话框询问用户是否要恢复之前的页面
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("恢复未关闭的页面")
                .setMessage("检测到您有 ${savedUrls.size} 个未关闭的页面，请选择操作：")
                .setPositiveButton("恢复所有页面") { _, _ ->
                    Log.d(TAG, "用户选择：恢复所有页面")
                    // 恢复保存的卡片
                    gestureCardWebViewManager?.restoreCardsState()
                    // 延迟刷新UI状态，等待卡片恢复完成
                    Handler(Looper.getMainLooper()).postDelayed({
                        forceRefreshUIState()
                    }, 500)
                }
                .setNegativeButton("清除所有页面") { _, _ ->
                    Log.d(TAG, "用户选择：清除所有页面")
                    // 清除保存的状态
                    sharedPreferences.edit().remove("floating_card_urls").apply()
                    // 不调用restoreCardsState，直接刷新UI状态
                    forceRefreshUIState()
                }
                .setNeutralButton("稍后决定") { _, _ ->
                    Log.d(TAG, "用户选择：稍后决定")
                    // 暂时不处理，保持当前状态，不调用restoreCardsState
                    forceRefreshUIState()
                }
                .setCancelable(false)
                .show()
        } else {
            // 如果没有保存的卡片，立即刷新UI状态
            forceRefreshUIState()
        }
    }

    private fun setupBottomNavigation() {
        // 创建webview卡片切换手势检测器
        val webViewCardSwipeDetector = createWebViewCardSwipeDetector()

        // 初始化Material波浪追踪器
        setupMaterialWaveTracker()

        // 对话tab (最左边)
        findViewById<LinearLayout>(R.id.tab_chat)?.apply {
            setOnClickListener {
                deactivateStackedCardPreview()
                showChat()
                // 退出搜索tab手势遮罩区
                deactivateSearchTabGestureOverlay()
            }
            setupTabGestureDetection(this, webViewCardSwipeDetector)
        }

        // 搜索tab (第二位)
        findViewById<LinearLayout>(R.id.tab_search)?.apply {
            setOnClickListener {
                deactivateStackedCardPreview()
                showBrowser()
                // 单击搜索tab时，如果遮罩层已激活，则激活多卡片系统
                if (isSearchTabGestureOverlayActive) {
                    activateStackedCardPreview()
                }
                // 如果遮罩层未激活，则正常切换到搜索tab（不激活遮罩层）
            }

            // 设置长按监听器 - 长按激活/退出遮罩层
            setOnLongClickListener {
                Log.d(TAG, "搜索tab长按事件触发，当前遮罩层状态: $isSearchTabGestureOverlayActive")

                if (isSearchTabGestureOverlayActive) {
                    // 如果遮罩层已激活，长按退出遮罩层
                    Log.d(TAG, "长按搜索tab退出遮罩层")
                    deactivateSearchTabGestureOverlay()
                } else {
                    // 如果遮罩层未激活，长按激活遮罩层
                    Log.d(TAG, "长按搜索tab激活遮罩层")
                    deactivateStackedCardPreview()
                    showBrowser()
                    activateSearchTabGestureOverlay()
                }
                true // 消费长按事件
            }

            setupTabGestureDetection(this, webViewCardSwipeDetector)
        }

        // 任务tab (第三位，原首页)
        findViewById<LinearLayout>(R.id.tab_home)?.apply {
            setOnClickListener {
                deactivateStackedCardPreview()
                showTaskSelection()
                // 退出搜索tab手势遮罩区
                deactivateSearchTabGestureOverlay()
            }
            setupTabGestureDetection(this, webViewCardSwipeDetector)
        }

        // 语音tab (第四位)
        findViewById<LinearLayout>(R.id.tab_voice)?.apply {
            setOnClickListener {
                deactivateStackedCardPreview()
                showVoice()
                // 退出搜索tab手势遮罩区
                deactivateSearchTabGestureOverlay()
            }
            setupTabGestureDetection(this, webViewCardSwipeDetector)
        }

        // 软件tab (第五位)
        findViewById<LinearLayout>(R.id.tab_app_search)?.apply {
            setOnClickListener {
                deactivateStackedCardPreview()
                showAppSearch()
                // 退出搜索tab手势遮罩区
                deactivateSearchTabGestureOverlay()
            }
            setupTabGestureDetection(this, webViewCardSwipeDetector)
        }

        // 设置tab (最右边)
        findViewById<LinearLayout>(R.id.tab_settings)?.apply {
            setOnClickListener {
                deactivateStackedCardPreview()
                showSettings()
                // 退出搜索tab手势遮罩区
                deactivateSearchTabGestureOverlay()
            }
            setupTabGestureDetection(this, webViewCardSwipeDetector)
        }

        // 初始化搜索tab图标和徽标
        updateSearchTabIcon()
        updateSearchTabBadge()

        // 检测语音支持情况并隐藏语音tab（如果不支持）
        updateVoiceTabVisibility()

        // 设置底部tab区域的横滑手势
        setupTabAreaSwipeGesture()

        // 创建底部tab手势指示器
        createTabSwipeGestureIndicator()

        // 设置tab区域之间的滑动切换
        setupTabAreaSwipeForWebPageSwitch()

        // 创建tab滑动指示器
        createTabSwipeIndicator()

        // 创建对话tab横滑指示器
        createChatTabSwipeIndicator()

        // 搜索tab横滑功能已集成到全局横滑手势中
        // createSearchTabSwipeHotArea() // 已移除，避免冲突
    }

    /**
     * 更新语音tab的可见性
     */
    private fun updateVoiceTabVisibility() {
        try {
            // 检测语音支持情况
            val supportInfo = voiceInputManager.detectVoiceSupport()
            val voiceTab = findViewById<LinearLayout>(R.id.tab_voice)

            if (supportInfo.isSupported) {
                // 支持语音输入，显示语音tab
                voiceTab?.visibility = View.VISIBLE
                Log.d(TAG, "设备支持语音输入，显示语音tab")
            } else {
                // 不支持语音输入，隐藏语音tab
                voiceTab?.visibility = View.GONE
                Log.d(TAG, "设备不支持语音输入，隐藏语音tab")

                // 如果当前正在语音页面，切换到任务页面
                if (currentState == UIState.VOICE) {
                    showTaskSelection()
                }
            }

            // 更新tab的权重分配
            updateTabWeights()

        } catch (e: Exception) {
            Log.e(TAG, "更新语音tab可见性失败", e)
            // 出错时默认隐藏语音tab
            findViewById<LinearLayout>(R.id.tab_voice)?.visibility = View.GONE
        }
    }

    /**
     * 更新tab的权重分配
     */
    private fun updateTabWeights() {
        val bottomNav = findViewById<LinearLayout>(R.id.bottom_navigation) ?: return
        val voiceTab = findViewById<LinearLayout>(R.id.tab_voice)

        // 计算可见tab的数量
        var visibleTabCount = 0
        for (i in 0 until bottomNav.childCount) {
            val tabView = bottomNav.getChildAt(i)
            if (tabView.visibility == View.VISIBLE) {
                visibleTabCount++
            }
        }

        // 重新分配权重
        for (i in 0 until bottomNav.childCount) {
            val tabView = bottomNav.getChildAt(i) as? LinearLayout ?: continue
            val layoutParams = tabView.layoutParams as LinearLayout.LayoutParams

            if (tabView.visibility == View.VISIBLE) {
                layoutParams.weight = 1f
            } else {
                layoutParams.weight = 0f
            }

            tabView.layoutParams = layoutParams
        }
    }

    /**
     * 更新搜索tab的图标 - 使用FaviconLoader
     */
    private fun updateSearchTabIcon() {
        val searchTab = findViewById<LinearLayout>(R.id.tab_search)
        val searchTabIcon = searchTab?.findViewById<ImageView>(R.id.search_tab_icon)

        if (searchTabIcon != null && currentSearchEngine != null) {
            // 使用当前搜索引擎的图标
            com.example.aifloatingball.utils.FaviconLoader.loadIcon(
                searchTabIcon,
                currentSearchEngine!!.url,
                R.drawable.ic_search
            )
        }
    }

    /**
     * 更新搜索tab的徽标 - 显示后台已开启的网页数量
     */
    private fun updateSearchTabBadge() {
        val searchTab = findViewById<LinearLayout>(R.id.tab_search)
        val searchTabBadge = searchTab?.findViewById<TextView>(R.id.search_tab_badge)

        if (searchTabBadge != null) {
            // 获取当前打开的网页数量
            val cardCount = gestureCardWebViewManager?.getAllCards()?.size ?: 0
            
            if (cardCount > 0) {
                searchTabBadge.text = cardCount.toString()
                searchTabBadge.visibility = View.VISIBLE
            } else {
                searchTabBadge.visibility = View.GONE
            }
        }
    }

    /**
     * 设置搜索引擎RecyclerView
     */
    private fun setupSearchEngineRecyclerView() {
        try {
            // 设置LayoutManager
            browserPreviewEngineList.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)

            // 完全移除搜索引擎列表，不显示任何搜索引擎
            val emptyEngines = emptyList<com.example.aifloatingball.model.SearchEngine>()

            // 初始化适配器
            draggableEngineAdapter = com.example.aifloatingball.adapter.DraggableSearchEngineAdapter(
                engines = emptyEngines.toMutableList(),
                onEngineClick = { engine ->
                    // 点击搜索引擎时的处理
                    val searchText = browserSearchInput.text.toString().trim()
                    if (searchText.isNotEmpty()) {
                        val searchUrl = engine.getSearchUrl(searchText)
                        loadBrowserContent(searchUrl)
                    } else {
                        // 如果没有搜索文本，打开搜索引擎主页
                        val baseUrl = engine.url.split("?")[0]
                        loadBrowserContent(baseUrl)
                    }
                },
                onEngineReorder = { reorderedEngines ->
                    // 保存新的排序到设置中
                    saveSearchEngineOrder(reorderedEngines)
                }
            )

            // 设置适配器
            browserPreviewEngineList.adapter = draggableEngineAdapter

            // 附加拖动功能
            draggableEngineAdapter.attachToRecyclerView(browserPreviewEngineList)

            Log.d(TAG, "搜索引擎RecyclerView设置完成")
        } catch (e: Exception) {
            Log.e(TAG, "设置搜索引擎RecyclerView失败", e)
        }
    }

    /**
     * 保存搜索引擎排序
     */
    private fun saveSearchEngineOrder(engines: List<com.example.aifloatingball.model.SearchEngine>) {
        try {
            // 这里可以保存到SharedPreferences或数据库
            val engineNames = engines.map { it.name }
            val editor = getSharedPreferences("search_engine_order", MODE_PRIVATE).edit()
            editor.putString("engine_order", engineNames.joinToString(","))
            editor.apply()
            Log.d(TAG, "搜索引擎排序已保存: ${engineNames.joinToString(", ")}")
        } catch (e: Exception) {
            Log.e(TAG, "保存搜索引擎排序失败", e)
        }
    }

    private fun setupSearchListeners() {
        // 搜索按钮点击
        directSearchButton.setOnClickListener {
            performDirectSearch()
        }

        // 搜索框回车
        directSearchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performDirectSearch()
                true
            } else {
                false
            }
        }
    }

    private fun performDirectSearch() {
        val query = directSearchInput.text.toString().trim()
        if (query.isNotEmpty()) {
            // 使用内嵌浏览器进行搜索
            showBrowser()
            browserSearchInput.setText(query)
            performBrowserSearch()
        }
    }

    private fun setupTaskSelection() {
        taskAdapter = TaskTemplateAdapter(SimpleTaskTemplates.templates) { template ->
            selectTask(template)
        }
        taskRecyclerView.layoutManager = GridLayoutManager(this, 2)
        taskRecyclerView.adapter = taskAdapter
    }

    private fun selectTask(template: PromptTemplate) {
        Log.d(TAG, "选择任务: ${template.intentName}")
        currentTemplate = template
        userPromptData = UserPromptData(template.intentId)
        currentStepIndex = 0
        showStepGuidance()
    }

    private fun showChat() {
        currentState = UIState.CHAT
        chatLayout.visibility = View.VISIBLE
        taskSelectionLayout.visibility = View.GONE
        stepGuidanceLayout.visibility = View.GONE
        promptPreviewLayout.visibility = View.GONE
        voiceLayout.visibility = View.GONE
        appSearchLayout.visibility = View.GONE
        browserLayout.visibility = View.GONE
        settingsLayout.visibility = View.GONE

        // 同步群聊数据，确保显示最新的群聊列表
        try {
            syncGroupChatsFromUnifiedManager()
            // 刷新当前显示的联系人列表
            chatContactAdapter?.updateContacts(allContacts)
            Log.d(TAG, "showChat: 已同步群聊数据并刷新显示")
        } catch (e: Exception) {
            Log.e(TAG, "showChat: 同步群聊数据失败", e)
        }

        // 更新Tab颜色状态
        updateTabColors()
    }

    private fun showTaskSelection() {
        currentState = UIState.TASK_SELECTION
        chatLayout.visibility = View.GONE
        taskSelectionLayout.visibility = View.VISIBLE
        stepGuidanceLayout.visibility = View.GONE
        promptPreviewLayout.visibility = View.GONE
        voiceLayout.visibility = View.GONE
        appSearchLayout.visibility = View.GONE
        browserLayout.visibility = View.GONE
        settingsLayout.visibility = View.GONE

        // 更新Tab颜色状态
        updateTabColors()
    }

    private fun showStepGuidance() {
        currentState = UIState.STEP_GUIDANCE
        chatLayout.visibility = View.GONE
        taskSelectionLayout.visibility = View.GONE
        stepGuidanceLayout.visibility = View.VISIBLE
        promptPreviewLayout.visibility = View.GONE
        voiceLayout.visibility = View.GONE
        appSearchLayout.visibility = View.GONE
        browserLayout.visibility = View.GONE
        settingsLayout.visibility = View.GONE

        setupCurrentStep()
        // 更新Tab颜色状态
        updateTabColors()
    }

    private fun showPromptPreview() {
        currentState = UIState.PROMPT_PREVIEW
        chatLayout.visibility = View.GONE
        taskSelectionLayout.visibility = View.GONE
        stepGuidanceLayout.visibility = View.GONE
        promptPreviewLayout.visibility = View.VISIBLE
        voiceLayout.visibility = View.GONE
        appSearchLayout.visibility = View.GONE
        browserLayout.visibility = View.GONE
        settingsLayout.visibility = View.GONE

        generateFinalPrompt()
        // 更新Tab颜色状态
        updateTabColors()
    }

    private fun setupCurrentStep() {
        val template = currentTemplate ?: return
        val fields = template.fields

        Log.d(TAG, "设置当前步骤: $currentStepIndex, 总步骤数: ${fields.size}")

        if (currentStepIndex >= fields.size) {
            // 所有步骤完成，显示预览
            Log.d(TAG, "所有步骤完成，显示预览")
            showPromptPreview()
            return
        }

        val currentField = fields[currentStepIndex]

        // 更新标题和问题
        stepTitleText.text = "${template.intentName} - 步骤 ${currentStepIndex + 1}/${fields.size}"
        stepQuestionText.text = currentField.question

        // 隐藏所有输入组件
        stepInputLayout.visibility = View.GONE
        stepChoiceGroup.visibility = View.GONE
        stepMultiChoiceLayout.visibility = View.GONE

        // 根据字段类型显示对应的输入组件
        when (currentField.type) {
            FieldType.TEXT_INPUT -> {
                stepInputLayout.visibility = View.VISIBLE
                stepInputText.setText(userPromptData.collectedData[currentField.id]?.toString() ?: "")
            }
            FieldType.SINGLE_CHOICE -> {
                stepChoiceGroup.visibility = View.VISIBLE
                setupSingleChoice(currentField)
            }
            FieldType.MULTIPLE_CHOICE -> {
                stepMultiChoiceLayout.visibility = View.VISIBLE
                setupMultipleChoice(currentField)
            }
        }

        // 设置按钮状态
        val canGoPrev = currentStepIndex > 0
        prevStepButton.isEnabled = true  // 总是启用，因为第一步时可以返回首页
        prevStepButton.text = if (canGoPrev) "上一步" as CharSequence else "返回首页" as CharSequence
        skipStepButton.visibility = if (currentField.isOptional) View.VISIBLE else View.GONE

        Log.d(TAG, "上一步按钮状态: enabled=true, text=${prevStepButton.text}")

        // 设置按钮点击事件
        setupStepButtons(currentField)
    }

    private fun setupSingleChoice(field: PromptField) {
        stepChoiceGroup.removeAllViews()
        field.options?.forEachIndexed { index, option ->
            val radioButton = RadioButton(this).apply {
                text = option
                id = index
                isChecked = userPromptData.collectedData[field.id] == option
            }
            stepChoiceGroup.addView(radioButton)
        }
    }

    private fun setupMultipleChoice(field: PromptField) {
        stepMultiChoiceLayout.removeAllViews()
        val selectedOptions = userPromptData.collectedData[field.id] as? List<String> ?: emptyList()

        field.options?.forEach { option ->
            val checkBox = CheckBox(this).apply {
                text = option
                isChecked = selectedOptions.contains(option)
            }
            stepMultiChoiceLayout.addView(checkBox)
        }
    }

    private fun setupStepButtons(field: PromptField) {
        // 清除之前的监听器，避免重复设置
        nextStepButton.setOnClickListener(null)
        prevStepButton.setOnClickListener(null)
        skipStepButton.setOnClickListener(null)

        nextStepButton.setOnClickListener {
            Log.d(TAG, "下一步按钮被点击，当前步骤: $currentStepIndex")
            if (collectCurrentStepData(field)) {
                currentStepIndex++
                setupCurrentStep()
            }
        }

        prevStepButton.setOnClickListener {
            Log.d(TAG, "上一步按钮被点击，当前步骤: $currentStepIndex")
            if (currentStepIndex > 0) {
                currentStepIndex--
                Log.d(TAG, "返回到步骤: $currentStepIndex")
                setupCurrentStep()
            } else {
                // 如果是第一步，返回首页
                Log.d(TAG, "已是第一步，返回任务选择页面")
                showTaskSelection()
            }
        }

        skipStepButton.setOnClickListener {
            Log.d(TAG, "跳过按钮被点击，当前步骤: $currentStepIndex")
            if (field.isOptional) {
                currentStepIndex++
                setupCurrentStep()
            }
        }
    }

    private fun collectCurrentStepData(field: PromptField): Boolean {
        when (field.type) {
            FieldType.TEXT_INPUT -> {
                val text = stepInputText.text.toString().trim()
                if (text.isEmpty() && !field.isOptional) {
                    Toast.makeText(this, "请填写必填信息", Toast.LENGTH_SHORT).show()
                    return false
                }
                if (text.isNotEmpty()) {
                    userPromptData.collectedData[field.id] = text
                }
            }
            FieldType.SINGLE_CHOICE -> {
                val selectedId = stepChoiceGroup.checkedRadioButtonId
                if (selectedId == -1 && !field.isOptional) {
                    Toast.makeText(this, "请选择一个选项", Toast.LENGTH_SHORT).show()
                    return false
                }
                if (selectedId != -1) {
                    val selectedOption = field.options?.get(selectedId)
                    if (selectedOption != null) {
                        userPromptData.collectedData[field.id] = selectedOption
                    }
                }
            }
            FieldType.MULTIPLE_CHOICE -> {
                val selectedOptions = mutableListOf<String>()
                for (i in 0 until stepMultiChoiceLayout.childCount) {
                    val checkBox = stepMultiChoiceLayout.getChildAt(i) as CheckBox
                    if (checkBox.isChecked) {
                        selectedOptions.add(checkBox.text.toString())
                    }
                }
                if (selectedOptions.isEmpty() && !field.isOptional) {
                    Toast.makeText(this, "请至少选择一个选项", Toast.LENGTH_SHORT).show()
                    return false
                }
                if (selectedOptions.isNotEmpty()) {
                    userPromptData.collectedData[field.id] = selectedOptions
                }
            }
        }
        return true
    }

    private fun generateFinalPrompt() {
        val template = currentTemplate ?: return
        var finalPrompt = template.finalPromptFormat

        // 替换占位符
        userPromptData.collectedData.forEach { (key, value) ->
            val placeholder = "{$key}"
            val replacement = when (value) {
                is List<*> -> value.joinToString("、")
                else -> value.toString()
            }
            finalPrompt = finalPrompt.replace(placeholder, replacement)
        }

        finalPromptText.text = finalPrompt
        recommendedEnginesText.text = "推荐引擎：${template.recommendedEngines.joinToString("、")}"

        // 设置按钮事件
        executeSearchButton.setOnClickListener {
            executeSearch(finalPrompt, template.recommendedEngines.firstOrNull() ?: "deepseek")
        }

        backToTasksButton.setOnClickListener {
            showTaskSelection()
        }
    }

    private fun executeSearch(prompt: String, engineKey: String) {
        Log.d(TAG, "执行搜索: prompt='$prompt', engine='$engineKey'")

        // 使用内嵌浏览器进行搜索
        showBrowser()
        browserSearchInput.setText(prompt)

        // 根据引擎类型构建搜索URL
        val searchUrl = when (engineKey) {
            "Google" -> "https://www.google.com/search?q=${java.net.URLEncoder.encode(prompt, "UTF-8")}"
            "Bing" -> "https://www.bing.com/search?q=${java.net.URLEncoder.encode(prompt, "UTF-8")}"
            "百度" -> "https://www.baidu.com/s?wd=${java.net.URLEncoder.encode(prompt, "UTF-8")}"
            else -> "https://www.google.com/search?q=${java.net.URLEncoder.encode(prompt, "UTF-8")}"
        }

        loadBrowserContent(searchUrl)
    }

    private fun handleIntentData() {
        intent?.let {
            val searchContent = it.getStringExtra("search_content")
            if (!searchContent.isNullOrEmpty()) {
                // 如果有预填内容，可以选择直接填入第一个文本字段
                Log.d(TAG, "收到搜索内容: $searchContent")
                // TODO: 根据需要实现预填逻辑
            }
        }
    }

    override fun onBackPressed() {
        // 如果分支视图可见，则优先处理它的返回逻辑
        if (promptBranchManager.isBranchViewVisible) {
            if (promptBranchManager.canNavigateBack()) {
                promptBranchManager.navigateBack()
                } else {
                promptBranchManager.hideBranchView()
            }
            return // 消费返回事件
        }

        // 如果在浏览器页面，处理返回逻辑
        if (currentState == UIState.BROWSER) {
            // 如果卡片预览覆盖层可见，先关闭预览
            if (::cardPreviewOverlay.isInitialized && cardPreviewOverlay.visibility == View.VISIBLE) {
                cardPreviewOverlay.hide()
                return
            }

            // 如果手势提示覆盖层可见，先关闭提示
            if (browserGestureOverlay.visibility == View.VISIBLE) {
                hideGestureHint()
                return
            }

            // 如果抽屉打开，先关闭抽屉
            if (browserLayout.isDrawerOpen(GravityCompat.START)) {
                browserLayout.closeDrawer(GravityCompat.START)
                return
            }

            // 使用统一的返回逻辑
            performUnifiedWebViewBack("系统返回键")
            return
        }

        // 如果在步骤引导页面，处理返回逻辑
        if (currentState == UIState.STEP_GUIDANCE) {
            if (currentStepIndex > 0) {
                // 返回上一步
                currentStepIndex--
                setupCurrentStep()
            } else {
                // 如果是第一步，返回任务选择页面
                showTaskSelection()
            }
            return
        }

        // 如果在提示预览页面，返回步骤引导
        if (currentState == UIState.PROMPT_PREVIEW) {
            showStepGuidance()
            return
        }

        // 否则，执行Activity的默认返回逻辑
        super.onBackPressed()
    }

    /**
     * 分支视图隐藏时的回调
     */
    override fun onBranchViewHidden() {
        applyBackgroundBlur(false)
        setBottomNavigationEnabled(true) // 恢复底部导航
    }

    /**
     * 显示语音提示分支视图
     */
    private fun showPromptBranches() {
        if (promptBranchManager.isBranchViewVisible) {
            return
        }

        val rootView = window.decorView.rootView as ViewGroup
        val micCenterX = voiceMicContainer.x + voiceMicContainer.width / 2
        val micCenterY = voiceMicContainer.y + voiceMicContainer.height / 2

        promptBranchManager.showBranchView(rootView, voiceTextInput, micCenterX, micCenterY)
        applyBackgroundBlur(true)
        setBottomNavigationEnabled(false) // 禁用底部导航
    }

    /**
     * 启用或禁用底部导航栏
     */
    private fun setBottomNavigationEnabled(enabled: Boolean) {
        val bottomNav = findViewById<LinearLayout>(R.id.bottom_navigation)
        for (i in 0 until bottomNav.childCount) {
            val tab = bottomNav.getChildAt(i)
            tab.isEnabled = enabled
            tab.alpha = if (enabled) 1.0f else 0.5f
        }
    }

    /**
     * 应用背景模糊效果
     * @param blur 是否模糊
     */
    fun applyBackgroundBlur(blur: Boolean) {
        // 获取需要保持清晰的视图
        val textInputLayout = findViewById<LinearLayout>(R.id.voice_text_input_layout)

        if (blur) {
            // 应用模糊效果到整个布局
            voiceLayout.alpha = 0.7f

            // 保持文本输入框清晰
            textInputLayout.alpha = 1.0f
            textInputLayout.elevation = 10f

            // 隐藏麦克风容器
            voiceMicContainer.animate().alpha(0.3f).setDuration(200).start()
        } else {
            // 恢复正常显示
            voiceLayout.alpha = 1.0f
            textInputLayout.elevation = 0f
            voiceMicContainer.animate().alpha(1.0f).setDuration(200).start()
        }
    }

    private fun resetVoiceUI() {
        voiceTextInput.setText("")
        recognizedText = ""
        isListening = false
        voiceSearchButton.isEnabled = false

        // 根据当前语音支持情况设置UI状态
        val supportInfo = voiceSupportInfo
        if (supportInfo != null) {
            updateVoiceButtonState(supportInfo)
        } else {
            // 如果还没有检测结果，显示默认状态
            voiceStatusText.text = "正在检测语音功能..."
            voiceMicContainer.visibility = View.VISIBLE
        }

        // 恢复默认背景色
        voiceMicContainer.setCardBackgroundColor(ContextCompat.getColor(this, R.color.simple_mode_accent_light))
    }

    private fun toggleVoiceRecognition() {
        // 检查语音支持情况
        val supportInfo = voiceSupportInfo
        if (supportInfo == null) {
            voiceStatusText.text = "语音功能检测中，请稍后重试"
            return
        }

        if (isListening) {
            stopVoiceRecognition()
            return
        }

        // 如果按钮可见，说明支持SpeechRecognizer，直接启动
        if (supportInfo.isSupported) {
            startVoiceRecognition()
        } else {
            // 这种情况不应该发生，因为按钮已经隐藏了
            Log.w(TAG, "语音按钮被点击但不支持SpeechRecognizer")
            voiceStatusText.text = "语音功能不可用"
        }
    }



    private fun startVoiceRecognition() {
        // 检查权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.RECORD_AUDIO), 1001)
            return
        }

        // 直接使用SpeechRecognizer（因为按钮只在支持时才显示）
        // 释放之前的识别器
        releaseSpeechRecognizer()

        // 创建新的语音识别器
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(createVoiceRecognitionListener())

        // 创建识别意图
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        try {
            isListening = true
            updateVoiceListeningState(true)
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "SpeechRecognizer启动失败", e)
            voiceStatusText.text = "语音识别启动失败"
            isListening = false
            updateVoiceListeningState(false)
        }
    }

    private fun stopVoiceRecognition() {
        isListening = false
        speechRecognizer?.stopListening()
        updateVoiceListeningState(false)
    }

    private fun updateVoiceListeningState(listening: Boolean) {
        if (listening) {
            voiceStatusText.text = "正在倾听..."
            voiceMicContainer.setCardBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
            // 添加脉冲动画
            val scaleX = ObjectAnimator.ofFloat(voiceMicContainer, "scaleX", 1.0f, 1.1f, 1.0f)
            val scaleY = ObjectAnimator.ofFloat(voiceMicContainer, "scaleY", 1.0f, 1.1f, 1.0f)
            scaleX.repeatCount = ValueAnimator.INFINITE
            scaleY.repeatCount = ValueAnimator.INFINITE
            val animatorSet = AnimatorSet()
            animatorSet.playTogether(scaleX, scaleY)
            animatorSet.duration = 1000
            animatorSet.start()
        } else {
            voiceStatusText.text = "点击麦克风开始语音输入"
            voiceMicContainer.setCardBackgroundColor(ContextCompat.getColor(this, R.color.colorPrimary))
            voiceMicContainer.clearAnimation()
        }
    }

    private fun createVoiceRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                handler.post {
                    voiceStatusText.text = "请开始说话"
                }
            }

            override fun onBeginningOfSpeech() {
                handler.post {
                    voiceStatusText.text = "正在聆听..."
                }
            }

            override fun onRmsChanged(rmsdB: Float) {
                // 可以根据音量调整麦克风图标大小
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                handler.post {
                    voiceStatusText.text = "正在处理..."
                }
            }

            override fun onError(error: Int) {
                handler.post {
                    handleVoiceRecognitionError(error)
                }
            }

            override fun onResults(results: Bundle?) {
                handler.post {
                    processVoiceRecognitionResults(results)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                handler.post {
                    processVoicePartialResults(partialResults)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    private fun processVoiceRecognitionResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
            val newText = matches[0]
            recognizedText = if (recognizedText.isEmpty()) newText else "$recognizedText $newText"

            voiceTextInput.setText(recognizedText)
            voiceTextInput.setSelection(recognizedText.length)
            voiceStatusText.text = "识别完成，继续说话或点击搜索"
            voiceSearchButton.isEnabled = true

            // 自动重启识别以支持连续语音输入
            handler.postDelayed({
                if (isListening) {
                    startVoiceRecognition()
                }
            }, 250)
        }
    }

    private fun processVoicePartialResults(partialResults: Bundle?) {
        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
            val partialText = matches[0]
            val displayText = if (recognizedText.isEmpty()) partialText else "$recognizedText $partialText"
            voiceTextInput.setText(displayText)
            voiceTextInput.setSelection(displayText.length)
        }
    }

    private fun handleVoiceRecognitionError(error: Int) {
        val errorMessage = when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "音频错误"
            SpeechRecognizer.ERROR_NETWORK -> "网络错误"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "没有录音权限"
            SpeechRecognizer.ERROR_NO_MATCH -> "未识别到语音"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "识别服务忙"
            SpeechRecognizer.ERROR_SERVER -> "服务器错误"
            else -> "识别错误"
        }

        // 对于非致命错误，自动重试
        if (error == SpeechRecognizer.ERROR_NO_MATCH ||
            error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
            error == SpeechRecognizer.ERROR_CLIENT) {
            voiceStatusText.text = "请再说一遍"
            handler.postDelayed({
                if (isListening) {
                    startVoiceRecognition()
                }
            }, 500)
        } else if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ||
                   error == SpeechRecognizer.ERROR_SERVER ||
                   error == SpeechRecognizer.ERROR_NETWORK) {
            // 对于服务相关错误，尝试系统语音输入
            Log.w(TAG, "SpeechRecognizer服务错误，尝试系统语音输入: $errorMessage")
            isListening = false
            updateVoiceListeningState(false)
            trySystemVoiceInput()
        } else {
            voiceStatusText.text = errorMessage
            isListening = false
            updateVoiceListeningState(false)
        }
    }

    private fun clearVoiceText() {
        voiceTextInput.setText("")
        recognizedText = ""
        voiceSearchButton.isEnabled = false
    }

    /**
     * 尝试使用系统语音输入
     */
    private fun trySystemVoiceInput() {
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
                voiceStatusText.text = "启动系统语音输入..."
                startActivityForResult(intent, SYSTEM_VOICE_REQUEST_CODE)
            } else {
                // 没有可用的语音识别应用
                showVoiceRecognitionNotAvailableDialog()
            }
        } catch (e: Exception) {
            Log.e(TAG, "启动系统语音输入失败: ${e.message}")
            showVoiceRecognitionNotAvailableDialog()
        }
    }

    /**
     * 显示语音识别不可用对话框 - 已修改为自动切换到手动输入
     */
    private fun showVoiceRecognitionNotAvailableDialog() {
        // 不再显示弹窗，直接切换到手动输入模式
        Log.d(TAG, "语音识别不可用，自动切换到手动输入模式")

        // 允许用户手动输入文本
        voiceStatusText.text = "请在下方输入文本，然后点击搜索"
        voiceMicContainer.setCardBackgroundColor(ContextCompat.getColor(this, android.R.color.darker_gray))
        voiceTextInput.requestFocus()

        // 显示键盘
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(voiceTextInput, InputMethodManager.SHOW_IMPLICIT)

        // 确保搜索按钮可用
        voiceSearchButton.isEnabled = true
        Log.d(TAG, "已切换到手动输入模式")
    }

    private fun executeVoiceSearch() {
        val query = voiceTextInput.text.toString().trim()
        if (query.isNotEmpty()) {
            // 停止语音识别
            stopVoiceRecognition()
            releaseSpeechRecognizer()

            // 使用内嵌浏览器进行搜索
            showBrowser()
            browserSearchInput.setText(query)
            performBrowserSearch()
        } else {
            Toast.makeText(this, "请先输入搜索内容", Toast.LENGTH_SHORT).show()
        }
    }

    private fun releaseSpeechRecognizer() {
        speechRecognizer?.apply {
            cancel()
            destroy()
        }
        speechRecognizer = null
        isListening = false
    }

    private fun minimizeToService() {
        // 简化权限检查，如果没有权限就直接关闭应用
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M && !android.provider.Settings.canDrawOverlays(this)) {
            Log.w(TAG, "No overlay permission, closing app instead of minimizing")
            finish()
            return
        }

        // 先执行淡出动画
            val rootView = findViewById<View>(android.R.id.content)
            rootView.animate()
            .alpha(0.5f)
                .setDuration(200)
                .setListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        // 启动最小化服务
                        val serviceIntent = Intent(this@SimpleModeActivity, SimpleModeService::class.java).apply {
                            action = SimpleModeService.ACTION_MINIMIZE
                        }
                        startService(serviceIntent)

                        // 延迟一点时间再关闭Activity，确保服务有足够时间创建窗口
                        handler.postDelayed({
                            // 关闭Activity
                            finish()
                            overridePendingTransition(0, 0) // 禁用Activity切换动画
                        }, 300) // 300ms延迟
                    }
                })
                .start()
    }

    /**
     * 切换到悬浮球模式
     */
    private fun switchToFloatingBallMode() {
        Log.d(TAG, "Switching to floating ball mode")
        try {
            // 停止简易模式服务
            if (SimpleModeService.isRunning(this)) {
                stopService(Intent(this, SimpleModeService::class.java))
            }



            // 检查悬浮窗权限
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M && android.provider.Settings.canDrawOverlays(this)) {
                // 启动悬浮球服务
                val serviceIntent = Intent(this, FloatingWindowService::class.java)
                startService(serviceIntent)

                // 悬浮球模式下不需要启动HomeActivity，直接关闭当前Activity
                Log.d(TAG, "Floating ball service started, closing SimpleModeActivity")
                finish()
            } else {
                // 没有权限，提示用户
                Toast.makeText(this, "需要悬浮窗权限才能使用悬浮球模式", Toast.LENGTH_LONG).show()
                // 恢复到简易模式选择
                settingsManager.setDisplayMode("simple_mode")
                loadSettings()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error switching to floating ball mode", e)
            Toast.makeText(this, "切换到悬浮球模式失败", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 切换到灵动岛模式
     */
    private fun switchToDynamicIslandMode() {
        Log.d(TAG, "Switching to dynamic island mode")
        try {
            // 停止简易模式服务
            if (SimpleModeService.isRunning(this)) {
                stopService(Intent(this, SimpleModeService::class.java))
            }



            // 检查悬浮窗权限
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M && android.provider.Settings.canDrawOverlays(this)) {
                // 启动灵动岛服务
                val serviceIntent = Intent(this, DynamicIslandService::class.java)
                startService(serviceIntent)

                // 灵动岛模式下不需要启动HomeActivity，直接关闭当前Activity
                Log.d(TAG, "Dynamic island service started, closing SimpleModeActivity")
                finish()
            } else {
                // 没有权限，提示用户
                Toast.makeText(this, "需要悬浮窗权限才能使用灵动岛模式", Toast.LENGTH_LONG).show()
                // 恢复到简易模式选择
                settingsManager.setDisplayMode("simple_mode")
                loadSettings()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error switching to dynamic island mode", e)
            Toast.makeText(this, "切换到灵动岛模式失败", Toast.LENGTH_SHORT).show()
        }
    }

    private fun closeSimpleMode() {
        Log.d(TAG, "closeSimpleMode() called")
        try {
            // 1. 标题栏按钮已移除，跳过按钮禁用

            // 2. 停止 SimpleModeService 服务（如果正在运行）
            Log.d(TAG, "Stopping SimpleModeService if running")
            if (SimpleModeService.isRunning(this)) {
                stopService(Intent(this, SimpleModeService::class.java))
            }

            // 3. 告诉 SettingsManager 我们要退出简易模式
            // 切换到悬浮球模式
            settingsManager.setDisplayMode("floating_ball")
            Log.d(TAG, "Changed display mode to floating_ball")

            // 4. 启动悬浮球服务，确保用户仍然可以访问应用功能
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M && android.provider.Settings.canDrawOverlays(this)) {
                Log.d(TAG, "Starting FloatingWindowService")
                val serviceIntent = Intent(this, FloatingWindowService::class.java)
                serviceIntent.putExtra("closing_from_simple_mode", true)
                startService(serviceIntent)

                Log.d(TAG, "FloatingWindowService started, not launching HomeActivity")
            } else {
                Log.w(TAG, "No overlay permission, cannot start floating ball service")
                Toast.makeText(this, "需要悬浮窗权限才能使用悬浮球模式", Toast.LENGTH_SHORT).show()
            }

            // 5. 使用 finishAndRemoveTask() 彻底关闭活动和任务
            Log.d(TAG, "Calling finishAndRemoveTask()")
            finishAndRemoveTask()

        } catch (e: Exception) {
            Log.e(TAG, "Error during closing", e)
            // 标题栏按钮已移除，跳过按钮状态恢复
            Toast.makeText(this, "关闭失败，请重试", Toast.LENGTH_SHORT).show()
        }
    }

    // 保留旧方法以避免其他地方的引用出错
    private fun closeAndSwitchToFloatingBall() {
        // 直接调用新方法
        closeSimpleMode()
    }



    override fun onResume() {
        super.onResume()
        try {
            // 重新加载联系人数据，确保新创建的群聊能够显示
            val savedContacts = loadSavedContacts()
            if (savedContacts.isNotEmpty()) {
                allContacts = savedContacts.toMutableList()
                
                // 重新加载群聊数据并合并到联系人列表中
                try {
                    val groupChats = unifiedGroupChatManager.getAllGroupChats()
                    Log.d(TAG, "onResume: 从UnifiedGroupChatManager加载到 ${groupChats.size} 个群聊")
                    if (groupChats.isNotEmpty()) {
                        // 将GroupChat转换为ChatContact
                        val groupChatContacts = groupChats.map { groupChat ->
                            ChatContact(
                                id = groupChat.id,
                                name = groupChat.name,
                                avatar = groupChat.avatar,
                                type = ContactType.GROUP,
                                description = groupChat.description,
                                isOnline = true,
                                lastMessage = groupChat.lastMessage,
                                lastMessageTime = groupChat.lastMessageTime,
                                unreadCount = groupChat.unreadCount,
                                isPinned = groupChat.isPinned,
                                isMuted = groupChat.isMuted,
                                groupId = groupChat.id,
                                memberCount = groupChat.members.size,
                                aiMembers = groupChat.members.filter { member -> member.type == MemberType.AI }.map { member -> member.name }
                            )
                        }
                        
                        // 查找或创建"全部"分类，并添加群聊
                        val allCategoryIndex = allContacts.indexOfFirst { it.name == "全部" }
                        if (allCategoryIndex != -1) {
                            val allCategory = allContacts[allCategoryIndex]
                            val updatedContacts = allCategory.contacts.toMutableList()
                            
                            // 移除现有的群聊联系人，避免重复
                            updatedContacts.removeAll { it.type == ContactType.GROUP }
                            
                            // 添加最新的群聊数据
                            updatedContacts.addAll(groupChatContacts)
                            
                            // 重新排序
                            val sortedContacts = updatedContacts.sortedWith(
                                compareByDescending<ChatContact> { it.isPinned }
                                    .thenByDescending { it.lastMessageTime }
                            ).toMutableList()
                            
                            allContacts[allCategoryIndex] = allCategory.copy(contacts = sortedContacts)
                        } else {
                            // 如果"全部"分类不存在，创建新分类
                            allContacts.add(ContactCategory("全部", groupChatContacts.toMutableList()))
                        }
                        
                        Log.d(TAG, "onResume: 重新加载了 ${groupChats.size} 个群聊")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "onResume: 重新加载群聊数据失败", e)
                }
                
                chatContactAdapter?.updateContacts(allContacts)
                Log.d(TAG, "onResume: 重新加载了联系人数据")
                
                // 强制刷新AI联系人数据，确保显示最新的消息
                forceLoadContactDataSummary()
            }
        } catch (e: Exception) {
            Log.e(TAG, "onResume: 重新加载联系人数据失败", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // 注销API密钥同步广播接收器
        try {
            apiKeySyncReceiver?.let {
                unregisterReceiver(it)
                apiKeySyncReceiver = null
                Log.d(TAG, "API密钥同步广播接收器已注销")
            }
        } catch (e: Exception) {
            Log.e(TAG, "注销API密钥同步广播接收器失败", e)
        }
        
        // 注销AI消息更新广播接收器
        unregisterBroadcastReceiver()

        // 注销添加AI联系人广播接收器
        try {
            addAIContactReceiver?.let {
                unregisterReceiver(it)
                addAIContactReceiver = null
                Log.d(TAG, "添加AI联系人广播接收器已注销")
            }
        } catch (e: Exception) {
            Log.e(TAG, "注销添加AI联系人广播接收器失败", e)
        }

        // 注销AI对话更新广播接收器
        try {
            unregisterReceiver(aiChatUpdateReceiver)
            Log.d(TAG, "AI对话更新广播接收器已注销")
        } catch (e: Exception) {
            Log.e(TAG, "注销AI对话更新广播接收器失败", e)
        }

        // 停止文件监听同步机制
        stopFileSyncMonitoring()
        
        // 停止定时强制同步机制
        stopPeriodicSync()

        // 释放语音识别器
        releaseSpeechRecognizer()

        // 保存悬浮卡片状态
        gestureCardWebViewManager?.saveCardsState()
        
        // 清理多页面WebView管理器
        multiPageWebViewManager?.destroy()
        multiPageWebViewManager = null
        
        // 清理手势卡片WebView管理器
        gestureCardWebViewManager?.destroy()
        gestureCardWebViewManager = null

        // 清理四分之一圆弧操作栏
        quarterArcOperationBar?.let { operationBar ->
            (operationBar.parent as? ViewGroup)?.removeView(operationBar)
        }
        quarterArcOperationBar = null

        // 取消标签切换动画
        tabSwitchAnimationManager.cancelCurrentAnimation()

        // 清理应用搜索适配器资源
        
        // 移除数据变更监听器
        try {
            unifiedGroupChatManager.removeDataChangeListener(this)
        } catch (e: Exception) {
            Log.e(TAG, "移除数据变更监听器失败", e)
        }
        try {
            if (::appSearchAdapter.isInitialized) {
                appSearchAdapter.onDestroy()
            }
        } catch (e: Exception) {
            Log.e(TAG, "清理应用搜索适配器资源失败", e)
        }

        // 清理搜索tab手势遮罩区
        deactivateSearchTabGestureOverlay()

        // 移除所有延迟任务
        handler.removeCallbacksAndMessages(null)
        longPressHandler.removeCallbacksAndMessages(null)
    }

    /**
     * 启动图标预加载
     */
    private fun startIconPreloading() {
        lifecycleScope.launch {
            try {
                val iconPreloader = com.example.aifloatingball.manager.IconPreloader.getInstance(this@SimpleModeActivity)

                // 获取所有应用配置
                val allApps = appSearchSettings.getAppConfigs().filter { it.isEnabled }

                Log.d(TAG, "开始预加载${allApps.size}个应用图标")

                // 智能预加载 - 优先加载热门应用
                iconPreloader.preloadPopularApps(allApps) { progress, total ->
                    Log.d(TAG, "图标预加载进度: $progress/$total")

                    // 可以在这里更新UI显示预加载进度
                    // 例如在状态栏显示进度
                }

                Log.d(TAG, "图标预加载完成")

            } catch (e: Exception) {
                Log.e(TAG, "图标预加载失败", e)
            }
        }
    }

    override fun onPause() {
        super.onPause()

        // 在 onPause 中保存悬浮卡片状态，确保即使应用被系统杀死也能保存
        gestureCardWebViewManager?.saveCardsState()
        Log.d(TAG, "onPause: 已保存悬浮卡片状态")

        // 取消长按检测
        longPressRunnable?.let { longPressHandler.removeCallbacks(it) }

        // 隐藏分支视图
        if (::promptBranchManager.isInitialized) {
            promptBranchManager.hideBranchView()
        }

        // 恢复正常显示
        applyBackgroundBlur(false)
    }

    /**
     * 设置浏览器WebView - 手势卡片式版本
     */
    private fun setupBrowserWebView() {
        // 初始化手势卡片式WebView管理器
        gestureCardWebViewManager = GestureCardWebViewManager(
            context = this,
            container = browserWebViewContainer
        )

        // 设置页面变化监听器
        gestureCardWebViewManager?.setOnPageChangeListener(object : GestureCardWebViewManager.OnPageChangeListener {
            override fun onCardAdded(cardData: GestureCardWebViewManager.WebViewCardData, position: Int) {
                // 隐藏主页内容，显示全屏卡片界面
                browserHomeContent.visibility = View.GONE
                browserTabContainer.visibility = View.GONE

                // 关键修复：确保ViewPager2可见
                showViewPager2()

                // 更新搜索tab徽标
                updateSearchTabBadge()

                Log.d(TAG, "添加卡片: ${cardData.title}，ViewPager2已显示")
            }

            override fun onCardRemoved(cardData: GestureCardWebViewManager.WebViewCardData, position: Int) {
                // 如果没有卡片了，返回搜索tab主页
                if (gestureCardWebViewManager?.getAllCards()?.isEmpty() == true) {
                    // 确保切换到搜索tab
                    switchToSearchTab()
                    // 显示搜索tab主页
                    showBrowserHome()
                    Log.d(TAG, "最后一个卡片已关闭，返回搜索tab主页")
                }

                // 更新搜索tab徽标
                updateSearchTabBadge()

                Log.d(TAG, "移除卡片: ${cardData.title}")
            }

            override fun onCardSwitched(cardData: GestureCardWebViewManager.WebViewCardData, position: Int) {
                // 更新搜索框URL
                browserSearchInput.setText(cardData.url)

                Log.d(TAG, "切换到卡片: ${cardData.title}")
            }

            override fun onPreviewModeChanged(isPreview: Boolean) {
                // 预览模式变化
                Log.d(TAG, "预览模式变化: $isPreview")
            }

            override fun onPageTitleChanged(cardData: GestureCardWebViewManager.WebViewCardData, title: String) {
                // 更新页面标题
                Log.d(TAG, "卡片标题变化: $title")
            }

            override fun onPageLoadingStateChanged(cardData: GestureCardWebViewManager.WebViewCardData, isLoading: Boolean) {
                // 更新加载状态
                Log.d(TAG, "卡片加载状态变化: ${cardData.title} - $isLoading")
            }



            override fun onSwipePreviewStarted(cards: List<GestureCardWebViewManager.WebViewCardData>, currentIndex: Int) {
                // 显示横滑预览指示器
                showSwipePreviewIndicator(cards, currentIndex)
                Log.d(TAG, "开始横滑预览，当前索引: $currentIndex，总卡片数: ${cards.size}")
            }

            override fun onSwipePreviewUpdated(position: Int, positionOffset: Float) {
                // 更新横滑预览指示器位置
                updateSwipePreviewIndicator(position, positionOffset)
            }

            override fun onSwipePreviewEnded() {
                // 隐藏横滑预览指示器
                hideSwipePreviewIndicator()
                Log.d(TAG, "结束横滑预览")
            }

            override fun onGoHome() {
                // 返回简易模式搜索tab首页
                showBrowser() // 显示浏览器界面
                showBrowserHome() // 显示浏览器主页内容

                // 切换到搜索tab
                findViewById<LinearLayout>(R.id.tab_search)?.performClick()

                Log.d(TAG, "返回简易模式搜索tab首页")
            }

            override fun onPageRefresh() {
                // 刷新时的地址栏滚动动画
                showAddressBarRefreshAnimation()
                Log.d(TAG, "页面刷新")
            }

            override fun onAllCardsRemoved() {
                // 所有卡片都被关闭，显示浏览器首页
                Log.d(TAG, "所有卡片已关闭，显示浏览器首页")
                showBrowserHome()

                // 隐藏悬浮卡片预览
                stackedCardPreview?.visibility = View.GONE

                // 更新搜索tab徽标
                updateSearchTabBadge()

                // 显示提示信息
                Toast.makeText(this@SimpleModeActivity, "所有标签页已关闭", Toast.LENGTH_SHORT).show()
            }
        })

        // 卡片式浏览器不需要标签栏，移除相关监听器

        // 设置搜索框监听器
        browserSearchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performBrowserSearch()
                true
            } else {
                false
            }
        }

        // 设置清空按钮
        val browserBtnClear = findViewById<ImageButton>(R.id.browser_btn_clear)
        browserBtnClear?.setOnClickListener {
            browserSearchInput.setText("")
            browserBtnClear.visibility = View.GONE
            showMaterialToast("🗑️ 搜索框已清空")
        }

        // 设置AI机器人按钮
        val browserBtnAi = findViewById<ImageButton>(R.id.browser_btn_ai)
        browserBtnAi?.setOnClickListener {
            val query = browserSearchInput.text.toString().trim()
            if (query.isNotEmpty()) {
                // 如果有输入内容，使用AI搜索
                showMaterialToast("🤖 正在使用AI搜索: $query")
                performAISearch(query)
            } else {
                // 如果没有输入内容，打开AI助手
                showMaterialToast("🤖 打开AI助手")
                openAIAssistant()
            }
        }

        // 添加文本变化监听器，检测"app"关键词自动切换到应用搜索，并控制清空按钮显示
        browserSearchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim()?.lowercase() ?: ""

                // 控制清空按钮的显示/隐藏
                val clearButton = findViewById<ImageButton>(R.id.browser_btn_clear)
                clearButton?.visibility = if (s?.isNotEmpty() == true) View.VISIBLE else View.GONE

                // 当用户输入"app"时，自动切换到应用搜索界面
                if (query == "app") {
                    Log.d(TAG, "检测到用户输入'app'，自动切换到应用搜索界面")
                    // 延迟切换，避免输入过程中的干扰
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (browserSearchInput.text.toString().trim().lowercase() == "app") {
                            switchToAppSearchWithQuery(null)
                            // 清空浏览器搜索框
                            browserSearchInput.setText("")
                            showMaterialToast("📱 已切换到应用搜索")
                        }
                    }, 500) // 500ms延迟，给用户时间完成输入
                }
            }
        })

        // 设置手势检测
        setupBrowserGestureDetector()

        // 设置按钮监听器
        setupBrowserButtons()

        // 设置卡片预览按钮图标
        setupCardPreviewButtonIcon()

        // 设置抽屉
        setupBrowserDrawer()

        // 设置快捷方式
        setupBrowserShortcuts()

        // 初始化搜索引擎
        initializeBrowserSearchEngine()

        // 设置搜索引擎选择器
        setupBrowserSearchEngineSelector()

        // 设置卡片预览功能
        setupCardPreviewFeature()
        
        // 注意：不在这里自动恢复卡片状态，让用户通过对话框选择

        // 设置手势提示功能
        setupGestureHintFeature()

        // 设置四分之一圆弧操作栏
        setupQuarterArcOperationBar()

        // 初始显示主页内容
        showBrowserHome()
    }

    /**
     * 设置四分之一圆弧操作栏
     */
    private fun setupQuarterArcOperationBar() {
        Log.d(TAG, "开始设置四分之一圆弧操作栏")

        quarterArcOperationBar = QuarterArcOperationBar(this).apply {
            // 设置布局参数 - 使用足够大的尺寸确保不被截断
            val size = (300 * resources.displayMetrics.density).toInt()
            layoutParams = FrameLayout.LayoutParams(size, size).apply {
                // 根据左手模式设置位置
                val isLeftHanded = settingsManager.isLeftHandedModeEnabled()
                gravity = if (isLeftHanded) {
                    android.view.Gravity.BOTTOM or android.view.Gravity.START
                } else {
                    android.view.Gravity.BOTTOM or android.view.Gravity.END
                }

                val margin = (16 * resources.displayMetrics.density).toInt()
                if (isLeftHanded) {
                    leftMargin = margin
                } else {
                    rightMargin = margin
                }
                bottomMargin = margin
            }

            // 设置左手模式
            setLeftHandedMode(settingsManager.isLeftHandedModeEnabled())

            // 设置高层级
            elevation = 25f
            isClickable = true
            isFocusable = true

            // 设置操作监听器
            setOnOperationListener(object : QuarterArcOperationBar.OnOperationListener {
                override fun onRefresh() {
                    // 刷新当前页面
                    var refreshed = false

                    // 优先检查MobileCardManager
                    val mobileCurrentCard = mobileCardManager?.getCurrentCard()
                    if (mobileCurrentCard?.webView != null) {
                        mobileCurrentCard.webView.reload()
                        refreshed = true
                        Log.d(TAG, "四分之一圆弧操作栏: 手机卡片页面已刷新")
                    }

                    // 如果没有手机卡片，检查手势卡片
                    if (!refreshed) {
                        val gestureCurrentCard = gestureCardWebViewManager?.getCurrentCard()
                        if (gestureCurrentCard?.webView != null) {
                            gestureCurrentCard.webView.reload()
                            refreshed = true
                            Log.d(TAG, "四分之一圆弧操作栏: 手势卡片页面已刷新")
                        }
                    }

                    if (refreshed) {
                        Toast.makeText(this@SimpleModeActivity, "页面已刷新", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@SimpleModeActivity, "没有可刷新的页面", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onNextTab() {
                    // 切换到下一个标签
                    var switched = false

                    // 优先检查MobileCardManager
                    val mobileCards = mobileCardManager?.getAllCards()
                    if (!mobileCards.isNullOrEmpty() && mobileCards.size > 1) {
                        mobileCardManager?.switchToNextCard()
                        val currentCard = mobileCardManager?.getCurrentCard()
                        Toast.makeText(this@SimpleModeActivity, "已切换到: ${currentCard?.title ?: "下一个标签"}", Toast.LENGTH_SHORT).show()
                        switched = true
                        Log.d(TAG, "四分之一圆弧操作栏: 手机卡片切换到下一个标签")
                    }

                    // 如果没有手机卡片或只有一个，检查手势卡片
                    if (!switched) {
                        val gestureCards = gestureCardWebViewManager?.getAllCards()
                        if (!gestureCards.isNullOrEmpty() && gestureCards.size > 1) {
                            gestureCardWebViewManager?.switchToNextCard()
                            val currentCard = gestureCardWebViewManager?.getCurrentCard()
                            Toast.makeText(this@SimpleModeActivity, "已切换到: ${currentCard?.title ?: "下一个标签"}", Toast.LENGTH_SHORT).show()
                            switched = true
                            Log.d(TAG, "四分之一圆弧操作栏: 手势卡片切换到下一个标签")
                        }
                    }

                    if (!switched) {
                        Toast.makeText(this@SimpleModeActivity, "没有其他标签可切换", Toast.LENGTH_SHORT).show()
                        Log.d(TAG, "四分之一圆弧操作栏: 没有可切换的标签")
                    }
                }

                override fun onBack() {
                    // 使用统一的返回逻辑
                    performUnifiedWebViewBack("圆弧操作栏")
                    Log.d(TAG, "四分之一圆弧操作栏: 执行返回操作")
                }

                override fun onUndoClose() {
                    // 撤回关闭：创建新标签页作为临时实现
                    var created = false

                    // 优先在MobileCardManager中创建新标签
                    if (mobileCardManager != null) {
                        val newCard = mobileCardManager?.addNewCard("about:blank")
                        if (newCard != null) {
                            Toast.makeText(this@SimpleModeActivity, "已创建新标签页", Toast.LENGTH_SHORT).show()
                            created = true
                            Log.d(TAG, "四分之一圆弧操作栏: 手机卡片创建新标签")
                        }
                    }

                    // 如果手机卡片管理器不可用，在手势卡片中创建
                    if (!created && gestureCardWebViewManager != null) {
                        val newCard = gestureCardWebViewManager?.addNewCard("about:blank")
                        if (newCard != null) {
                            Toast.makeText(this@SimpleModeActivity, "已创建新标签页", Toast.LENGTH_SHORT).show()
                            created = true
                            Log.d(TAG, "四分之一圆弧操作栏: 手势卡片创建新标签")
                        }
                    }

                    if (!created) {
                        Toast.makeText(this@SimpleModeActivity, "无法创建新标签", Toast.LENGTH_SHORT).show()
                        Log.d(TAG, "四分之一圆弧操作栏: 创建新标签失败")
                    }
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  }
            })

            // 设置配置监听器
            setOnConfigListener(object : QuarterArcOperationBar.OnConfigListener {
                override fun onShowConfig() {
                    // 显示配置对话框
                    quarterArcOperationBar?.showConfigDialog(supportFragmentManager, settingsManager)
                }
            })

            // 设置位置变化监听器
            setOnPositionChangeListener(object : QuarterArcOperationBar.OnPositionChangeListener {
                override fun onPositionChanged(x: Float, y: Float) {
                    // 保存新位置到设置
                    // 这里可以添加位置保存逻辑
                    Log.d(TAG, "圆弧操作栏位置已更新: ($x, $y)")
                }
            })

            // 设置可见性变化监听器
            setOnVisibilityChangeListener(object : QuarterArcOperationBar.OnVisibilityChangeListener {
                override fun onVisibilityChanged(isMinimized: Boolean) {
                    val statusText = if (isMinimized) "圆弧操作栏已最小化" else "圆弧操作栏已恢复"
                    Toast.makeText(this@SimpleModeActivity, statusText, Toast.LENGTH_SHORT).show()
                    Log.d(TAG, "圆弧操作栏可见性已更新: 最小化=$isMinimized")
                }
            })
        }

        // 添加到WebView容器中
        browserWebViewContainer.addView(quarterArcOperationBar)

        Log.d(TAG, "四分之一圆弧操作栏创建完成")
    }



    /**
     * 设置浏览器手势检测
     */
    private fun setupBrowserGestureDetector() {
        browserGestureDetector = GestureDetectorCompat(this, object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                // 双击显示手势提示
                showBrowserGestureHint("双击检测")
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                // 长按显示菜单
                showBrowserGestureHint("长按检测")
            }

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                // 手势滑动检测
                if (Math.abs(velocityX) > Math.abs(velocityY)) {
                    if (velocityX > 0) {
                        showBrowserGestureHint("向右滑动")
                    } else {
                        showBrowserGestureHint("向左滑动")
                    }
                }
                return true
            }
        })

        // 为WebView容器设置手势监听
        browserWebViewContainer.setOnTouchListener { _, event ->
            // 如果抽屉已经打开，不处理手势，让抽屉优先处理触摸事件
            if (browserLayout.isDrawerOpen(GravityCompat.START) || browserLayout.isDrawerOpen(GravityCompat.END)) {
                return@setOnTouchListener false
            }

            // 检查是否有层叠卡片预览正在显示
            val isStackedPreviewVisible = stackedCardPreview?.visibility == View.VISIBLE

            if (isStackedPreviewVisible) {
                // 悬浮卡片预览模式下，将触摸事件传递给StackedCardPreview处理
                Log.d(TAG, "悬浮卡片预览可见，传递触摸事件给StackedCardPreview")

                // 将触摸坐标转换为StackedCardPreview的坐标系
                stackedCardPreview?.let { preview ->
                    val location = IntArray(2)
                    browserWebViewContainer.getLocationOnScreen(location)
                    val previewLocation = IntArray(2)
                    preview.getLocationOnScreen(previewLocation)

                    val relativeX = location[0] - previewLocation[0] + event.x
                    val relativeY = location[1] - previewLocation[1] + event.y

                    // 创建相对坐标的触摸事件
                    val relativeEvent = MotionEvent.obtain(
                        event.downTime,
                        event.eventTime,
                        event.action,
                        relativeX,
                        relativeY,
                        event.metaState
                    )

                    // 传递给StackedCardPreview处理长按滑动
                    val handled = preview.dispatchTouchEvent(relativeEvent)
                    relativeEvent.recycle()

                    Log.d(TAG, "触摸事件传递结果: $handled, 动作: ${event.action}")
                    handled
                } ?: false
            } else {
                // 正常模式下，处理边缘侧滑和原有手势
                val edgeHandled = handleEdgeSwipeGesture(event)
                if (edgeHandled) {
                    // 边缘侧滑已处理，直接返回
                    return@setOnTouchListener true
                }
                
                // 处理其他手势
                val gestureHandled = browserGestureDetector.onTouchEvent(event)
                
                // 如果边缘侧滑或手势检测处理了事件，就消费掉
                edgeHandled || gestureHandled
            }
        }

        // 设置全局触摸监听
        setupGlobalTouchListener()
    }

    /**
     * 处理边缘侧滑手势
     */
    private fun handleEdgeSwipeGesture(event: MotionEvent): Boolean {
        // 检查是否在边缘区域
        val screenWidth = resources.displayMetrics.widthPixels
        val edgeThreshold = 50 // 边缘检测阈值，50dp
        
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                edgeSwipeStartX = event.x
                edgeSwipeStartY = event.y
                edgeSwipeStartTime = System.currentTimeMillis()
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                val isInLeftEdge = edgeSwipeStartX < edgeThreshold
                val isInRightEdge = edgeSwipeStartX > screenWidth - edgeThreshold
                
                if (isInLeftEdge || isInRightEdge) {
                    val deltaX = event.x - edgeSwipeStartX
                    val deltaY = event.y - edgeSwipeStartY
                    
                    // 检查是否为水平滑动且速度足够
                    if (Math.abs(deltaX) > Math.abs(deltaY) && Math.abs(deltaX) > 30) {
                        // 左边缘向右滑动或右边缘向左滑动
                        val isSwipeBack = (isInLeftEdge && deltaX > 0) || (isInRightEdge && deltaX < 0)
                        
                        if (isSwipeBack) {
                            // 在搜索tab中执行网页后退，其他tab中消费事件防止系统处理
                            if (currentState == UIState.BROWSER) {
                                showBrowserGestureHint("边缘滑动检测")
                            }
                            return true
                        }
                    }
                }
                return false
            }
            MotionEvent.ACTION_UP -> {
                val currentTime = System.currentTimeMillis()
                val deltaTime = currentTime - edgeSwipeStartTime
                val deltaX = event.x - edgeSwipeStartX
                val deltaY = event.y - edgeSwipeStartY
                
                // 检查是否为有效的边缘侧滑
                if (deltaTime < 500 && Math.abs(deltaX) > 100 && Math.abs(deltaX) > Math.abs(deltaY)) {
                    val isInLeftEdge = edgeSwipeStartX < edgeThreshold
                    val isInRightEdge = edgeSwipeStartX > screenWidth - edgeThreshold
                    
                    val isSwipeBack = (isInLeftEdge && deltaX > 0) || (isInRightEdge && deltaX < 0)
                    
                    if (isSwipeBack) {
                        if (currentState == UIState.BROWSER) {
                            // 在搜索tab中执行网页后退
                            performWebViewBack()
                        } else {
                            // 在其他tab中消费事件，防止系统处理导致应用退出
                            Log.d(TAG, "边缘侧滑在其他tab中，消费事件防止系统处理")
                        }
                        return true
                    }
                }
                return false
            }
        }
        return false
    }
    
    // 边缘侧滑相关变量
    private var edgeSwipeStartX = 0f
    private var edgeSwipeStartY = 0f
    private var edgeSwipeStartTime = 0L
    
    /**
     * 统一的WebView后退处理方法
     * 被系统返回键、圆弧操作栏、边缘侧滑等所有返回操作调用
     */
    private fun performUnifiedWebViewBack(source: String) {
        Log.d(TAG, "执行统一WebView后退操作，来源: $source")
        
        var handled = false

        // 优先检查MobileCardManager
        val mobileCurrentCard = mobileCardManager?.getCurrentCard()
        if (mobileCurrentCard?.webView?.canGoBack() == true) {
            mobileCurrentCard.webView.goBack()
            if (source == "边缘侧滑") {
                showBrowserGestureHint("网页后退")
            }
            handled = true
            Log.d(TAG, "$source：手机卡片返回上一页")
        }

        // 如果MobileCardManager没有处理，检查GestureCardWebViewManager
        if (!handled) {
            val gestureCurrentCard = gestureCardWebViewManager?.getCurrentCard()
            if (gestureCurrentCard?.webView?.canGoBack() == true) {
                gestureCurrentCard.webView.goBack()
                if (source == "边缘侧滑") {
                    showBrowserGestureHint("网页后退")
                }
                handled = true
                Log.d(TAG, "$source：手势卡片返回上一页")
            }
        }

        // 如果都没有处理，检查MultiPageWebViewManager
        if (!handled) {
            if (multiPageWebViewManager?.canGoBack() == true) {
                multiPageWebViewManager?.goBack()
                if (source == "边缘侧滑") {
                    showBrowserGestureHint("网页后退")
                }
                handled = true
                Log.d(TAG, "$source：多页面管理器返回上一页")
            }
        }

        // 如果没有可返回的页面，返回搜索tab首页
        if (!handled) {
            showBrowserHome()
            if (source == "边缘侧滑") {
                showBrowserGestureHint("返回搜索首页")
            }
            Log.d(TAG, "$source：无可返回页面，显示搜索tab首页")
        }
    }

    /**
     * 执行WebView后退操作（边缘侧滑专用）
     */
    private fun performWebViewBack() {
        performUnifiedWebViewBack("边缘侧滑")
    }

    /**
     * 设置全局触摸监听，确保悬浮卡片在任何地方都能响应触摸
     */
    private fun setupGlobalTouchListener() {
        val mainLayout = findViewById<LinearLayout>(R.id.simple_mode_main_layout)
        mainLayout?.setOnTouchListener { view, event ->
            // 检查是否有悬浮卡片预览正在显示
            val isStackedPreviewVisible = stackedCardPreview?.visibility == View.VISIBLE

            if (isStackedPreviewVisible) {
                // 悬浮卡片预览模式下，将触摸事件传递给StackedCardPreview处理
                Log.d(TAG, "全局触摸，悬浮卡片预览可见，传递触摸事件给StackedCardPreview")

                // 将触摸坐标转换为StackedCardPreview的坐标系
                stackedCardPreview?.let { preview ->
                    val location = IntArray(2)
                    view.getLocationOnScreen(location)
                    val previewLocation = IntArray(2)
                    preview.getLocationOnScreen(previewLocation)

                    val relativeX = location[0] - previewLocation[0] + event.x
                    val relativeY = location[1] - previewLocation[1] + event.y

                    // 创建相对坐标的触摸事件
                    val relativeEvent = MotionEvent.obtain(
                        event.downTime,
                        event.eventTime,
                        event.action,
                        relativeX,
                        relativeY,
                        event.metaState
                    )

                    // 传递给StackedCardPreview处理长按滑动
                    val handled = preview.dispatchTouchEvent(relativeEvent)
                    relativeEvent.recycle()

                    Log.d(TAG, "全局触摸事件传递结果: $handled, 动作: ${event.action}")
                    // 如果悬浮卡片处理了事件，就消费掉，否则让其他组件处理
                    handled
                } ?: false
            } else {
                // 没有悬浮卡片时，不消费事件，让其他组件正常处理
                false
            }
        }
    }

    /**
     * 显示浏览器手势提示
     */
    private fun showBrowserGestureHint(text: String) {
        browserGestureHint.text = text
        browserGestureHint.visibility = View.VISIBLE

        // 2秒后隐藏提示
        handler.postDelayed({
            browserGestureHint.visibility = View.GONE
        }, 2000)
    }

    /**
     * 处理浏览器返回按钮点击
     * @param isLongPress 是否为长按
     */
    private fun handleBrowserBackButtonClick(isLongPress: Boolean) {
        Log.d(TAG, "浏览器返回按钮${if (isLongPress) "长按" else "短按"}")

        if (isLongPress) {
            // 长按：返回搜索tab首页（不跳转到全局首页）
            showBrowserHome()
            Log.d(TAG, "长按返回搜索tab首页")
        } else {
            // 短按：返回上一页
            var handled = false

            // 优先检查MobileCardManager
            val mobileCurrentCard = mobileCardManager?.getCurrentCard()
            if (mobileCurrentCard?.webView?.canGoBack() == true) {
                mobileCurrentCard.webView.goBack()
                handled = true
                Log.d(TAG, "短按：手机卡片返回上一页")
            }

            // 如果MobileCardManager没有处理，检查GestureCardWebViewManager
            if (!handled) {
                val gestureCurrentCard = gestureCardWebViewManager?.getCurrentCard()
                if (gestureCurrentCard?.webView?.canGoBack() == true) {
                    gestureCurrentCard.webView.goBack()
                    handled = true
                    Log.d(TAG, "短按：手势卡片返回上一页")
                }
            }

            // 如果都没有处理，检查MultiPageWebViewManager
            if (!handled) {
                if (multiPageWebViewManager?.canGoBack() == true) {
                    multiPageWebViewManager?.goBack()
                    handled = true
                    Log.d(TAG, "短按：多页面管理器返回上一页")
                }
            }

            // 如果没有可返回的页面，返回搜索tab首页
            if (!handled) {
                showBrowserHome()
                Log.d(TAG, "短按：无可返回页面，显示搜索tab首页")
            }
        }
    }

    /**
     * 设置浏览器按钮监听器
     */
    private fun setupBrowserButtons() {
        // 卡片预览按钮 - 显示所有打开的页面卡片（原返回按钮）
        browserBtnClose.setOnClickListener {
            showCardPreviewDialog() // 短按显示卡片预览
        }

        // 设置长按监听器 - 保留返回功能
        browserBtnClose.setOnLongClickListener {
            handleBrowserBackButtonClick(true) // 长按返回
            true
        }

        // 菜单按钮 - 打开搜索引擎侧边栏
        browserBtnMenu.setOnClickListener {
            Log.d(TAG, "菜单按钮被点击")
            if (browserLayout.isDrawerOpen(GravityCompat.START)) {
                Log.d(TAG, "关闭抽屉")
                browserLayout.closeDrawer(GravityCompat.START)
            } else {
                Log.d(TAG, "打开抽屉")
                browserLayout.openDrawer(GravityCompat.START)
            }
        }

        // 开始浏览按钮 - 创建新卡片
        findViewById<com.google.android.material.button.MaterialButton>(R.id.browser_start_browsing_button)?.setOnClickListener { button ->
            // 添加点击动画
            button.animate()
                .scaleX(0.95f)
                .scaleY(0.95f)
                .setDuration(100)
                .withEndAction {
                    button.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(100)
                        .withEndAction {
                            // 动画完成后执行操作
                            performCreateNewCard()
                        }
                        .start()
                }
                .start()

            Log.d(TAG, "用户点击开始浏览按钮")
        }

        // 手势指南按钮
        findViewById<com.google.android.material.button.MaterialButton>(R.id.browser_gesture_guide_button)?.setOnClickListener {
            try {
                Log.d(TAG, "用户点击手势指南按钮")

                // 检查Activity状态
                if (isFinishing || isDestroyed) {
                    Log.w(TAG, "Activity正在结束或已销毁，跳过手势指南操作")
                    return@setOnClickListener
                }

                // 检查组件是否已初始化
                if (!::browserGestureOverlay.isInitialized) {
                    Log.e(TAG, "browserGestureOverlay未初始化，无法操作手势指南")
                    showMaterialToast("❌ 手势指南功能暂时不可用")
                    return@setOnClickListener
                }

                // 检查当前状态
                if (browserGestureOverlay.visibility == View.VISIBLE) {
                    Log.d(TAG, "手势指南已显示，隐藏它")
                    hideGestureHint()
                } else {
                    Log.d(TAG, "显示手势指南")
                    // 先隐藏其他覆盖层（但不包括手势指南本身）
                    if (::cardPreviewOverlay.isInitialized && cardPreviewOverlay.visibility == View.VISIBLE) {
                        cardPreviewOverlay.hide()
                    }

                    // 延迟显示手势指南
                    browserLayout.postDelayed({
                        if (!isFinishing && !isDestroyed) {
                            showGestureHint()
                        }
                    }, 100)
                }
            } catch (e: Exception) {
                Log.e(TAG, "手势指南按钮点击处理失败", e)
                showMaterialToast("❌ 手势指南功能出现错误")
            }
        }

        // 设置手势卡片管理器监听器
        gestureCardWebViewManager?.setOnPageChangeListener(object : GestureCardWebViewManager.OnPageChangeListener {
            override fun onCardAdded(cardData: GestureCardWebViewManager.WebViewCardData, position: Int) {
                Log.d(TAG, "卡片已添加: ${cardData.title}")
            }

            override fun onCardRemoved(cardData: GestureCardWebViewManager.WebViewCardData, position: Int) {
                Log.d(TAG, "卡片已移除: ${cardData.title}")
            }

            override fun onCardSwitched(cardData: GestureCardWebViewManager.WebViewCardData, position: Int) {
                Log.d(TAG, "切换到卡片: ${cardData.title}")
            }

            override fun onPreviewModeChanged(isPreview: Boolean) {
                Log.d(TAG, "预览模式变化: $isPreview")
            }

            override fun onPageTitleChanged(cardData: GestureCardWebViewManager.WebViewCardData, title: String) {
                Log.d(TAG, "页面标题变化: $title")
            }

            override fun onPageLoadingStateChanged(cardData: GestureCardWebViewManager.WebViewCardData, isLoading: Boolean) {
                Log.d(TAG, "页面加载状态变化: $isLoading")
            }

            override fun onGoHome() {
                // 返回浏览器首页
                showBrowserHome()
                Log.d(TAG, "返回浏览器首页")
            }

            override fun onPageRefresh() {
                // 页面刷新
                Log.d(TAG, "页面刷新")
            }

            override fun onSwipePreviewStarted(cards: List<GestureCardWebViewManager.WebViewCardData>, currentIndex: Int) {
                // 显示横滑预览指示器
                showSwipePreviewIndicator(cards, currentIndex)
                Log.d(TAG, "开始横滑预览，当前索引: $currentIndex，总卡片数: ${cards.size}")
            }

            override fun onSwipePreviewUpdated(position: Int, positionOffset: Float) {
                // 更新横滑预览指示器位置
                updateSwipePreviewIndicator(position, positionOffset)
            }

            override fun onSwipePreviewEnded() {
                // 隐藏横滑预览指示器
                hideSwipePreviewIndicator()
                Log.d(TAG, "结束横滑预览")
            }

            override fun onAllCardsRemoved() {
                // 所有卡片都被关闭，显示浏览器首页
                Log.d(TAG, "所有卡片已关闭，显示浏览器首页")
                showBrowserHome()

                // 隐藏悬浮卡片预览
                stackedCardPreview?.visibility = View.GONE

                // 更新搜索tab徽标
                updateSearchTabBadge()

                // 显示提示信息
                Toast.makeText(this@SimpleModeActivity, "所有标签页已关闭", Toast.LENGTH_SHORT).show()
            }
        })
    }

    /**
     * 设置浏览器抽屉 - 完整功能版本
     */
    private fun setupBrowserDrawer() {
        // 设置抽屉监听器
        browserLayout.addDrawerListener(object : DrawerLayout.DrawerListener {
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                // 更新透明度和动画
                drawerView.alpha = 0.3f + (0.7f * slideOffset)
                Log.d(TAG, "抽屉滑动，偏移量: $slideOffset")
            }

            override fun onDrawerOpened(drawerView: View) {
                drawerView.alpha = 1.0f
                Log.d(TAG, "抽屉已打开")
                // 打开抽屉时更新搜索引擎列表
                updateBrowserEngineList('#')
            }

            override fun onDrawerClosed(drawerView: View) {
                Log.d(TAG, "抽屉已关闭")
            }

            override fun onDrawerStateChanged(newState: Int) {
                Log.d(TAG, "抽屉状态改变: $newState")
            }
        })

        // 设置退出按钮点击监听器
        browserExitButton.setOnClickListener {
            browserLayout.closeDrawer(GravityCompat.START)
        }

        // 确保抽屉可以被触摸和滑动
        browserLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)

        // 启用抽屉边缘滑动手势
        browserLayout.setScrimColor(0x99000000.toInt()) // 设置半透明遮罩

        Log.d(TAG, "浏览器抽屉设置完成，锁定模式: ${browserLayout.getDrawerLockMode(GravityCompat.START)}")

        // 设置字母索引栏
        setupBrowserLetterIndexBar()
    }

    /**
     * 设置浏览器快捷方式
     */
    private fun setupBrowserShortcuts() {
        browserShortcutsGrid.layoutManager = GridLayoutManager(this, 4)

        // 完全移除网站快捷方式，不显示任何网站图标
        val shortcuts = emptyList<BrowserShortcut>()

        // 设置适配器
        val adapter = BrowserShortcutAdapter(shortcuts) { shortcut ->
            loadBrowserContent(shortcut.url)
        }
        browserShortcutsGrid.adapter = adapter
    }

    /**
     * 浏览器快捷方式数据类
     */
    data class BrowserShortcut(
        val name: String,
        val url: String,
        val iconResId: Int
    )

    /**
     * 浏览器快捷方式适配器
     */
    inner class BrowserShortcutAdapter(
        private val shortcuts: List<BrowserShortcut>,
        private val onShortcutClick: (BrowserShortcut) -> Unit
    ) : androidx.recyclerview.widget.RecyclerView.Adapter<BrowserShortcutAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.shortcut_icon)
            val name: TextView = view.findViewById(R.id.shortcut_name)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_browser_shortcut, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val shortcut = shortcuts[position]
            holder.name.text = shortcut.name

            // 使用FaviconLoader加载快捷方式图标
            com.example.aifloatingball.utils.FaviconLoader.loadIcon(
                holder.icon,
                shortcut.url,
                shortcut.iconResId
            )

            holder.itemView.setOnClickListener {
                onShortcutClick(shortcut)
            }
        }

        override fun getItemCount() = shortcuts.size
    }

    /**
     * 初始化浏览器搜索引擎
     */
    private fun initializeBrowserSearchEngine() {
        // 获取默认搜索引擎
        val defaultEngines = com.example.aifloatingball.model.SearchEngine.DEFAULT_ENGINES
        currentSearchEngine = if (defaultEngines.isNotEmpty()) {
            defaultEngines[0] // 使用第一个作为默认搜索引擎
        } else {
            // 如果没有默认引擎，创建一个Google搜索引擎
            com.example.aifloatingball.model.SearchEngine(
                name = "Google",
                displayName = "Google",
                url = "https://www.google.com/search?q={query}",
                iconResId = R.drawable.ic_google,
                description = "Google搜索"
            )
        }

        Log.d(TAG, "初始化默认搜索引擎: ${currentSearchEngine?.name}")

        // 更新搜索tab图标
        updateSearchTabIcon()
    }

    /**
     * 设置浏览器搜索引擎选择器
     */
    private fun setupBrowserSearchEngineSelector() {
        Log.d(TAG, "开始设置浏览器搜索引擎选择器")

        browserSearchEngineSelector.setOnEngineClickListener(object : MaterialSearchEngineSelector.OnEngineClickListener {
            override fun onEngineClick(engine: MaterialSearchEngineSelector.SearchEngine) {
                Log.d(TAG, "搜索引擎按钮被点击: ${engine.displayName}")

                // 获取搜索框中的文本
                val searchText = browserSearchInput.text.toString().trim()
                Log.d(TAG, "搜索框文本: '$searchText'")

                if (searchText.isNotEmpty()) {
                    // 如果有搜索文本，执行搜索
                    val searchUrl = engine.getSearchUrl(searchText)
                    Log.d(TAG, "执行搜索，URL: $searchUrl")
                    loadBrowserContent(searchUrl)

                    // 高亮选中的搜索引擎
                    browserSearchEngineSelector.highlightEngine(engine.name)

                    Log.d(TAG, "使用${engine.displayName}搜索: $searchText")
                } else {
                    // 如果没有搜索文本，打开搜索引擎主页
                    Log.d(TAG, "打开搜索引擎主页，URL: ${engine.url}")
                    loadBrowserContent(engine.url)

                    Log.d(TAG, "打开${engine.displayName}主页")
                }
            }
        })

        Log.d(TAG, "浏览器搜索引擎选择器设置完成")
    }

    /**
     * 设置卡片预览功能
     */
    private fun setupCardPreviewFeature() {
        // 创建卡片预览覆盖层
        cardPreviewOverlay = CardPreviewOverlay(this)
        cardPreviewOverlay.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ).apply {
            topMargin = 150 // 添加顶部边距，避免覆盖搜索tab区域
        }
        cardPreviewOverlay.visibility = View.GONE

        // 关键修复：将覆盖层添加到根布局而不是WebView容器
        // 这样可以避免与搜索引擎界面混合
        browserLayout.addView(cardPreviewOverlay)

        Log.d(TAG, "卡片预览覆盖层已添加到根布局")

        // 设置卡片预览按钮点击事件
        browserPreviewCardsButton.setOnClickListener {
            // 先隐藏其他覆盖层
            hideAllOverlays()
            // 延迟显示卡片预览，确保其他覆盖层完全隐藏
            browserLayout.postDelayed({
                showCardPreview()
            }, 100)
        }

        // 设置卡片预览覆盖层监听器
        cardPreviewOverlay.setOnCardClickListener(object : CardPreviewOverlay.OnCardClickListener {
            override fun onCardClick(cardData: GestureCardWebViewManager.WebViewCardData, position: Int) {
                // 隐藏卡片预览
                cardPreviewOverlay.hide()

                // 切换到指定卡片
                gestureCardWebViewManager?.switchToCard(position)

                // 确保UI状态正确
                browserLayout.postDelayed({
                    forceRefreshUIState()
                }, 300) // 等待隐藏动画完成

                Log.d(TAG, "切换到卡片: ${cardData.title}")
            }

            override fun onCardClose(cardData: GestureCardWebViewManager.WebViewCardData, position: Int) {
                // 关闭指定卡片
                gestureCardWebViewManager?.removeCard(position)

                // 如果还有卡片，更新预览
                val remainingCards = gestureCardWebViewManager?.getAllCards() ?: emptyList()
                if (remainingCards.isNotEmpty()) {
                    cardPreviewOverlay.show(remainingCards)
                } else {
                    // 没有卡片了，隐藏预览并返回搜索tab
                    cardPreviewOverlay.hide()
                    
                    // 切换到搜索tab
                    browserLayout.postDelayed({
                        switchToSearchTab()
                        Log.d(TAG, "最后一个卡片已关闭，返回搜索tab")
                    }, 300) // 等待隐藏动画完成
                }

                Log.d(TAG, "关闭卡片: ${cardData.title}")
            }
        })

        cardPreviewOverlay.setOnCloseListener(object : CardPreviewOverlay.OnCloseListener {
            override fun onClose() {
                Log.d(TAG, "关闭卡片预览")

                // 确保返回到正确的状态
                browserLayout.postDelayed({
                    forceRefreshUIState()
                }, 100)
            }
        })

        cardPreviewOverlay.setOnAddCardListener(object : CardPreviewOverlay.OnAddCardListener {
            override fun onAddCard() {
                // 隐藏预览界面
                cardPreviewOverlay.hide()

                // 创建新卡片
                browserLayout.postDelayed({
                    performCreateNewCard()
                }, 300) // 等待隐藏动画完成

                Log.d(TAG, "从卡片预览创建新卡片")
            }
        })

        Log.d(TAG, "卡片预览功能设置完成")
    }

    /**
     * 设置手势提示功能
     */
    private fun setupGestureHintFeature() {
        // 设置手势提示关闭按钮
        browserGestureHintClose.setOnClickListener {
            hideGestureHint()
        }

        // 点击覆盖层关闭提示
        browserGestureOverlay.setOnClickListener {
            hideGestureHint()
        }

        // 首次使用时显示手势提示
        val sharedPrefs = getSharedPreferences("gesture_hints", MODE_PRIVATE)
        val hasShownHint = sharedPrefs.getBoolean("has_shown_gesture_hint", false)

        if (!hasShownHint) {
            // 延迟3秒显示手势提示
            handler.postDelayed({
                showGestureHint()
                sharedPrefs.edit().putBoolean("has_shown_gesture_hint", true).apply()
            }, 3000)
        }

        Log.d(TAG, "手势提示功能设置完成")
    }

    /**
     * 显示卡片预览
     */
    private fun showCardPreview() {
        // 合并所有管理器的卡片
        val gestureCards = gestureCardWebViewManager?.getAllCards() ?: emptyList()
        val mobileCards = mobileCardManager?.getAllCards() ?: emptyList()
        val allCards = mutableListOf<GestureCardWebViewManager.WebViewCardData>()

        allCards.addAll(gestureCards)
        allCards.addAll(mobileCards)

        Log.d(TAG, "卡片预览 - 手势卡片: ${gestureCards.size}, 手机卡片: ${mobileCards.size}, 总计: ${allCards.size}")

        if (allCards.isNotEmpty()) {
            // 确保卡片预览覆盖层在最前面
            cardPreviewOverlay.bringToFront()
            cardPreviewOverlay.show(allCards)
            Log.d(TAG, "显示卡片预览，卡片数: ${allCards.size}")
        } else {
            Toast.makeText(this, "暂无卡片", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 显示新建卡片预览（集成到预览卡片系统）
     */
    private fun showNewCardPreview() {
        try {
            Log.d(TAG, "显示新建卡片预览")

            // 合并所有管理器的卡片
            val gestureCards = gestureCardWebViewManager?.getAllCards() ?: emptyList()
            val mobileCards = mobileCardManager?.getAllCards() ?: emptyList()
            val allCards = mutableListOf<GestureCardWebViewManager.WebViewCardData>()

            allCards.addAll(gestureCards)
            allCards.addAll(mobileCards)

            Log.d(TAG, "新建卡片预览 - 手势卡片: ${gestureCards.size}, 手机卡片: ${mobileCards.size}, 总计: ${allCards.size}")

            if (allCards.isNotEmpty()) {
                // 使用层叠卡片预览系统显示新建的卡片
                stackedCardPreview?.apply {
                    // 转换为StackedCardPreview的数据格式
                    val stackedCards = allCards.map { card ->
                        com.example.aifloatingball.views.StackedCardPreview.WebViewCardData(
                            title = card.title,
                            url = card.url,
                            favicon = card.favicon,
                            screenshot = card.webView?.let { webView ->
                                // 尝试获取WebView截图
                                try {
                                    val bitmap = Bitmap.createBitmap(
                                        webView.width,
                                        webView.height,
                                        Bitmap.Config.ARGB_8888
                                    )
                                    val canvas = Canvas(bitmap)
                                    webView.draw(canvas)
                                    bitmap
                                } catch (e: Exception) {
                                    Log.w(TAG, "获取WebView截图失败", e)
                                    null
                                }
                            }
                        )
                    }

                    // 设置卡片数据
                    setWebViewCards(stackedCards)

                    // 重置为层叠模式
                    resetToStackedMode()

                    // 启用交互
                    enableStackedInteraction()

                    // 显示预览，使用淡入动画
                    alpha = 0f
                    visibility = View.VISIBLE
                    animate()
                        .alpha(1f)
                        .setDuration(300)
                        .start()

                    Log.d(TAG, "层叠卡片预览已显示，包含 ${stackedCards.size} 张卡片")
                }

                // 给用户操作提示（简化版本，避免过长）
                val message = "新卡片已创建！左右滑动查看，上滑关闭，点击打开"
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

            } else {
                Log.w(TAG, "没有卡片可以预览")
                Toast.makeText(this, "没有卡片可以预览", Toast.LENGTH_SHORT).show()
            }

        } catch (e: Exception) {
            Log.e(TAG, "显示新建卡片预览失败", e)
            Toast.makeText(this, "显示卡片预览失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 显示手势提示
     */
    private fun showGestureHint() {
        try {
            // 安全检查：确保组件已初始化
            if (!::browserGestureOverlay.isInitialized) {
                Log.e(TAG, "browserGestureOverlay未初始化，无法显示手势提示")
                return
            }

            // 检查Activity状态
            if (isFinishing || isDestroyed) {
                Log.w(TAG, "Activity正在结束或已销毁，跳过显示手势提示")
                return
            }

            // 确保手势提示覆盖层在最前面
            browserGestureOverlay.bringToFront()
            browserGestureOverlay.visibility = View.VISIBLE
            browserGestureOverlay.alpha = 0f
            browserGestureOverlay.animate()
                .alpha(1f)
                .setDuration(300)
                .withStartAction {
                    Log.d(TAG, "开始显示手势提示动画")
                }
                .withEndAction {
                    Log.d(TAG, "手势提示动画完成")
                }
                .start()

            Log.d(TAG, "显示手势提示")

        } catch (e: Exception) {
            Log.e(TAG, "显示手势提示时发生错误", e)
        }
    }

    /**
     * 隐藏手势提示
     */
    private fun hideGestureHint() {
        try {
            // 安全检查：确保组件已初始化
            if (!::browserGestureOverlay.isInitialized) {
                Log.e(TAG, "browserGestureOverlay未初始化，无法隐藏手势提示")
                return
            }

            // 检查Activity状态
            if (isFinishing || isDestroyed) {
                Log.w(TAG, "Activity正在结束或已销毁，跳过隐藏手势提示")
                return
            }

            browserGestureOverlay.animate()
                .alpha(0f)
                .setDuration(200)
                .setListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationStart(animation: android.animation.Animator) {
                        Log.d(TAG, "开始隐藏手势提示动画")
                    }

                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        browserGestureOverlay.visibility = View.GONE
                        Log.d(TAG, "手势提示动画完成并隐藏")
                    }
                })
                .start()

            Log.d(TAG, "隐藏手势提示")

        } catch (e: Exception) {
            Log.e(TAG, "隐藏手势提示时发生错误", e)
        }
    }

    /**
     * 检查是否为广告或恶意URL - 增强版
     */
    private fun isAdOrMaliciousUrl(url: String): Boolean {
        val adKeywords = listOf(
            // 通用广告
            "googleads", "googlesyndication", "doubleclick", "adsystem",
            "amazon-adsystem", "facebook.com/tr", "google-analytics",

            // 中文广告平台
            "baidu.com/cpro", "pos.baidu.com", "cbjs.baidu.com",
            "union.360.cn", "tanx.com", "alimama.com", "taobao.com/go/act",
            "irs01.com", "irs03.com", "mediav.com", "adnxs.com",

            // 下载和安装相关
            "download", "install", "apk", "exe", "setup", "installer",

            // 弹窗和广告
            "popup", "alert", "confirm", "prompt", "modal", "overlay",
            "advertisement", "banner", "sponsor",

            // 倒计时广告
            "countdown", "timer", "seconds", "跳过广告", "广告倒计时",
            "skip-ad", "ad-skip", "ad-countdown", "ad-timer",

            // 新闻网站常见广告
            "sohu.com/a/", "163.com/dy/", "sina.com.cn/zt/",
            "qq.com/rain/", "people.com.cn/GB/",

            // 视频广告
            "video-ad", "pre-roll", "mid-roll", "post-roll",
            "youku.com/show_page/", "iqiyi.com/adv/",

            // 应用商店跳转
            "itunes.apple.com", "play.google.com/store",
            "app-download", "app-install"
        )

        val lowerUrl = url.lowercase()
        return adKeywords.any { keyword -> lowerUrl.contains(keyword) }
    }

    /**
     * 注入广告拦截JavaScript - 增强版
     */
    private fun injectAdBlockingScript(webView: WebView?) {
        val adBlockScript = """
            javascript:(function() {
                // 移除常见广告元素 - 扩展选择器
                var adSelectors = [
                    // 通用广告选择器
                    '[id*="ad"]', '[class*="ad"]', '[id*="banner"]', '[class*="banner"]',
                    '[id*="popup"]', '[class*="popup"]', '[id*="modal"]', '[class*="modal"]',
                    '.advertisement', '.ads', '.ad-container', '.banner-ad',
                    'iframe[src*="ads"]', 'iframe[src*="doubleclick"]',

                    // 倒计时广告选择器
                    '[id*="countdown"]', '[class*="countdown"]', '[id*="timer"]', '[class*="timer"]',
                    '[id*="skip"]', '[class*="skip"]', '.ad-skip', '.skip-ad',
                    '.countdown-ad', '.ad-countdown', '.timer-ad', '.ad-timer',

                    // 中文广告选择器
                    '[class*="广告"]', '[id*="广告"]', '[class*="推广"]', '[id*="推广"]',
                    '.ad-wrap', '.ad-box', '.ad-content', '.sponsor', '.promotion',

                    // 新闻网站特定广告
                    '.sohu-ad', '.sina-ad', '.netease-ad', '.qq-ad', '.tencent-ad',
                    '.news-ad', '.article-ad', '.content-ad',

                    // 视频广告
                    '.video-ad', '.player-ad', '.pre-roll', '.mid-roll', '.post-roll',

                    // 下载提示
                    '.download-tip', '.app-download', '.install-app', '.download-app',

                    // 弹窗遮罩
                    '.mask', '.overlay', '.dialog-mask', '.popup-mask'
                ];

                // 移除广告元素
                adSelectors.forEach(function(selector) {
                    try {
                        var elements = document.querySelectorAll(selector);
                        elements.forEach(function(el) {
                            if (el && el.parentNode) {
                                el.style.display = 'none';
                                el.parentNode.removeChild(el);
                            }
                        });
                    } catch(e) {}
                });

                // 阻止弹窗函数
                window.alert = function() { return false; };
                window.confirm = function() { return false; };
                window.prompt = function() { return null; };

                // 阻止页面跳转到应用商店
                var originalOpen = window.open;
                window.open = function(url, name, specs) {
                    if (url && (url.includes('itunes.apple.com') ||
                               url.includes('play.google.com') ||
                               url.includes('app-download') ||
                               url.includes('download'))) {
                        return null;
                    }
                    return originalOpen.call(this, url, name, specs);
                };

                // 移除包含特定文本的元素
                var textPatterns = ['跳过广告', '广告', '下载APP', '立即下载', '安装应用', 'Skip Ad'];
                textPatterns.forEach(function(pattern) {
                    var walker = document.createTreeWalker(
                        document.body,
                        NodeFilter.SHOW_TEXT,
                        null,
                        false
                    );
                    var textNodes = [];
                    var node;
                    while (node = walker.nextNode()) {
                        if (node.textContent.includes(pattern)) {
                            textNodes.push(node);
                        }
                    }
                    textNodes.forEach(function(textNode) {
                        var element = textNode.parentElement;
                        if (element && element.tagName !== 'SCRIPT') {
                            element.style.display = 'none';
                        }
                    });
                });

                // 定期清理动态加载的广告
                setInterval(function() {
                    adSelectors.forEach(function(selector) {
                        try {
                            var elements = document.querySelectorAll(selector);
                            elements.forEach(function(el) {
                                if (el && el.style.display !== 'none') {
                                    el.style.display = 'none';
                                }
                            });
                        } catch(e) {}
                    });
                }, 2000);

            })();
        """

        try {
            webView?.evaluateJavascript(adBlockScript, null)
            Log.d(TAG, "广告拦截脚本注入成功")
        } catch (e: Exception) {
            Log.w(TAG, "注入广告拦截脚本失败: ${e.message}")
        }
    }

    /**
     * 执行创建新卡片操作
     */
    private fun performCreateNewCard() {
        try {
            Log.d(TAG, "显示新建卡片选择弹窗")

            // 先隐藏所有覆盖层
            hideAllOverlays()
            clearAllOverlays()

            // 显示新建卡片选择弹窗
            showNewCardSelectionDialog()

        } catch (e: Exception) {
            Log.e(TAG, "显示新建卡片弹窗时发生错误", e)
            Toast.makeText(this, "显示新建卡片弹窗时发生错误: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 显示新建卡片选择弹窗
     */
    private fun showNewCardSelectionDialog() {
        try {
            Log.d(TAG, "开始显示新建卡片选择弹窗")
            
            // 检查Activity状态
            if (isFinishing || isDestroyed) {
                Log.w(TAG, "Activity正在结束或已销毁，跳过显示新建卡片弹窗")
                return
            }
            
            // 检查FragmentManager状态
            if (supportFragmentManager.isStateSaved) {
                Log.w(TAG, "FragmentManager状态已保存，跳过显示新建卡片弹窗")
                return
            }
            
            // 尝试显示复杂的新建卡片弹窗
            try {
                NewCardSelectionDialog.show(
                    context = this,
                    fragmentManager = supportFragmentManager,
                    onHistoryItemSelected = { historyEntry ->
                        // 从历史记录创建新卡片
                        createNewCardFromUrl(historyEntry.url, historyEntry.title)
                    },
                    onBookmarkItemSelected = { bookmarkEntry ->
                        // 从收藏创建新卡片
                        createNewCardFromUrl(bookmarkEntry.url, bookmarkEntry.title)
                    },
                    onCreateBlankCard = {
                        // 创建空白卡片
                        createNewBlankCard()
                    },
                    onDismiss = {
                        Log.d(TAG, "新建卡片弹窗已关闭")
                    }
                )
                
                Log.d(TAG, "新建卡片选择弹窗显示成功")
            } catch (e: Exception) {
                Log.w(TAG, "复杂新建卡片弹窗失败，使用简化版本", e)
                // 如果复杂弹窗失败，使用简化的新建卡片弹窗
                showSimpleNewCardDialog()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "显示新建卡片弹窗失败", e)
            Toast.makeText(this, "显示新建卡片弹窗失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 显示简化的新建卡片弹窗
     */
    private fun showSimpleNewCardDialog() {
        try {
            Log.d(TAG, "显示简化的新建卡片弹窗")
            
            val options = arrayOf("新建空白卡片", "输入网址", "取消")
            
            val builder = android.app.AlertDialog.Builder(this)
                .setTitle("新建卡片")
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> {
                            // 新建空白卡片
                            createNewBlankCard()
                        }
                        1 -> {
                            // 输入网址
                            showUrlInputDialog()
                        }
                        2 -> {
                            // 取消
                            Log.d(TAG, "用户取消新建卡片")
                        }
                    }
                }
                .setCancelable(true)
            
            builder.show()
            
        } catch (e: Exception) {
            Log.e(TAG, "显示简化新建卡片弹窗失败", e)
            Toast.makeText(this, "显示新建卡片弹窗失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 显示网址输入对话框
     */
    private fun showUrlInputDialog() {
        try {
            val input = android.widget.EditText(this).apply {
                hint = "输入网址或搜索内容"
                setText("https://")
            }
            
            val builder = android.app.AlertDialog.Builder(this)
                .setTitle("输入网址")
                .setView(input)
                .setPositiveButton("确定") { _, _ ->
                    val url = input.text.toString().trim()
                    if (url.isNotEmpty()) {
                        createNewCardFromUrl(url, "新页面")
                    }
                }
                .setNegativeButton("取消", null)
            
            builder.show()
            
        } catch (e: Exception) {
            Log.e(TAG, "显示网址输入对话框失败", e)
            Toast.makeText(this, "输入网址失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 从URL创建新卡片
     */
    private fun createNewCardFromUrl(url: String, title: String) {
        try {
            Log.d(TAG, "从URL创建新卡片: $url, 标题: $title")

            // 先隐藏所有覆盖层
            hideAllOverlays()
            clearAllOverlays()

            // 尝试使用MobileCardManager，如果失败则使用原有的管理器
            var newCard: GestureCardWebViewManager.WebViewCardData? = null

            try {
                // 初始化MobileCardManager（如果还没有）
                if (mobileCardManager == null) {
                    setupMobileCardManager()
                }

                // 创建新卡片
                newCard = mobileCardManager?.addNewCard(url)
            } catch (e: Exception) {
                Log.w(TAG, "MobileCardManager创建卡片失败，使用原有管理器", e)

                // 确保原有管理器已初始化
                if (gestureCardWebViewManager == null) {
                    setupBrowserWebView()
                }

                // 使用原有管理器创建卡片
                newCard = gestureCardWebViewManager?.addNewCard(url)
                
                // 如果原有管理器也失败，记录错误
                if (newCard == null) {
                    Log.e(TAG, "所有卡片管理器都无法创建卡片")
                    Toast.makeText(this, "无法创建卡片，请检查系统状态", Toast.LENGTH_SHORT).show()
                    return
                }
            }

            if (newCard != null) {
                Log.d(TAG, "新卡片创建成功: ${newCard.id}")

                // 检查卡片总数
                val mobileCardCount = mobileCardManager?.getAllCards()?.size ?: 0
                val gestureCardCount = gestureCardWebViewManager?.getAllCards()?.size ?: 0
                Log.d(TAG, "当前卡片数量 - 手机卡片: $mobileCardCount, 手势卡片: $gestureCardCount")

                // 显示成功提示
                Toast.makeText(this, "新卡片已在后台创建: $title", Toast.LENGTH_SHORT).show()

                // 不立即显示预览，让卡片在后台加载
                // showNewCardPreview()

                // 确保UI状态正确切换
                browserLayout.post {
                    forceRefreshUIState()
                }
            } else {
                Log.e(TAG, "新卡片创建失败")
                Toast.makeText(this, "创建卡片失败", Toast.LENGTH_SHORT).show()
            }

        } catch (e: Exception) {
            Log.e(TAG, "从URL创建新卡片时发生错误", e)
            Toast.makeText(this, "创建卡片时发生错误: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 创建空白卡片
     */
    private fun createNewBlankCard() {
        try {
            Log.d(TAG, "创建空白卡片")

            // 先隐藏所有覆盖层
            hideAllOverlays()
            clearAllOverlays()

            // 尝试使用MobileCardManager，如果失败则使用原有的管理器
            var newCard: GestureCardWebViewManager.WebViewCardData? = null

            try {
                // 初始化MobileCardManager（如果还没有）
                if (mobileCardManager == null) {
                    setupMobileCardManager()
                }

                // 创建新卡片
                newCard = mobileCardManager?.addNewCard("about:blank")
            } catch (e: Exception) {
                Log.w(TAG, "MobileCardManager创建卡片失败，使用原有管理器", e)

                // 确保原有管理器已初始化
                if (gestureCardWebViewManager == null) {
                    setupBrowserWebView()
                }

                // 使用原有管理器创建卡片
                newCard = gestureCardWebViewManager?.addNewCard("about:blank")
                
                // 如果原有管理器也失败，记录错误
                if (newCard == null) {
                    Log.e(TAG, "所有卡片管理器都无法创建空白卡片")
                    Toast.makeText(this, "无法创建空白卡片，请检查系统状态", Toast.LENGTH_SHORT).show()
                    return
                }
            }

            if (newCard != null) {
                Log.d(TAG, "空白卡片创建成功: ${newCard.id}")

                // 检查卡片总数
                val mobileCardCount = mobileCardManager?.getAllCards()?.size ?: 0
                val gestureCardCount = gestureCardWebViewManager?.getAllCards()?.size ?: 0
                Log.d(TAG, "当前卡片数量 - 手机卡片: $mobileCardCount, 手势卡片: $gestureCardCount")

                // 显示成功提示
                Toast.makeText(this, "空白卡片已创建 (总计: ${mobileCardCount + gestureCardCount})", Toast.LENGTH_SHORT).show()

                // 创建卡片后自动显示预览卡片系统
                showNewCardPreview()

                // 确保UI状态正确切换
                browserLayout.post {
                    forceRefreshUIState()
                }
            } else {
                Log.e(TAG, "空白卡片创建失败")
                Toast.makeText(this, "创建空白卡片失败", Toast.LENGTH_SHORT).show()
            }

        } catch (e: Exception) {
            Log.e(TAG, "创建空白卡片时发生错误", e)
            Toast.makeText(this, "创建空白卡片时发生错误: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 设置新的手机卡片管理器
     */
    private fun setupMobileCardManager() {
        try {
            Log.d(TAG, "开始设置手机卡片管理器")

            // 检查容器是否已初始化
            if (!::browserWebViewContainer.isInitialized) {
                Log.e(TAG, "browserWebViewContainer未初始化，无法设置手机卡片管理器")
                throw IllegalStateException("browserWebViewContainer未初始化")
            }

            mobileCardManager = MobileCardManager(
                context = this,
                container = browserWebViewContainer
            )

        // 设置卡片变化监听器
        mobileCardManager?.setOnCardChangeListener(object : MobileCardManager.OnCardChangeListener {
            override fun onCardAdded(card: GestureCardWebViewManager.WebViewCardData, position: Int) {
                // 隐藏主页内容，显示卡片界面
                browserHomeContent.visibility = View.GONE
                browserTabContainer.visibility = View.GONE
                showViewPager2()

                Log.d(TAG, "手机卡片已添加: ${card.title}")
            }

            override fun onCardRemoved(card: GestureCardWebViewManager.WebViewCardData, position: Int) {
                // 如果没有卡片了，显示主页
                if (mobileCardManager?.getAllCards()?.isEmpty() == true) {
                    showBrowserHome()
                }

                Log.d(TAG, "手机卡片已移除: ${card.title}")
            }

            override fun onCardSwitched(card: GestureCardWebViewManager.WebViewCardData, position: Int) {
                // 更新搜索框URL
                browserSearchInput.setText(card.url)

                Log.d(TAG, "切换到手机卡片: ${card.title}")
            }

            override fun onAllCardsRemoved() {
                // 所有卡片都被移除，显示主页
                showBrowserHome()
                
                // 更新搜索tab徽标
                updateSearchTabBadge()
                
                Log.d(TAG, "所有手机卡片已移除")
            }
        })

        Log.d(TAG, "手机卡片管理器设置完成")
        } catch (e: Exception) {
            Log.e(TAG, "设置手机卡片管理器失败", e)
            throw e
        }
    }

    /**
     * 统一管理所有覆盖层的显示状态
     */
    private fun hideAllOverlays(includeGestureHint: Boolean = true) {
        Log.d(TAG, "开始隐藏所有覆盖层，包括手势提示: $includeGestureHint")

        // 隐藏手势提示覆盖层
        if (includeGestureHint && browserGestureOverlay.visibility == View.VISIBLE) {
            hideGestureHint()
            Log.d(TAG, "手势提示覆盖层已隐藏")
        }

        // 隐藏卡片预览覆盖层
        if (::cardPreviewOverlay.isInitialized && cardPreviewOverlay.visibility == View.VISIBLE) {
            cardPreviewOverlay.hide()
            Log.d(TAG, "卡片预览覆盖层已隐藏")
        }

        Log.d(TAG, "覆盖层隐藏完成")
    }

    /**
     * 清除所有可能阻挡触摸的覆盖层
     */
    private fun clearAllOverlays() {
        Log.d(TAG, "开始清除所有覆盖层")

        // 1. 确保手势提示覆盖层完全隐藏和禁用
        findViewById<FrameLayout>(R.id.browser_gesture_overlay)?.let { overlay ->
            overlay.visibility = View.GONE
            overlay.isClickable = false
            overlay.isFocusable = false
            overlay.isEnabled = false
            Log.d(TAG, "手势提示覆盖层已完全禁用")
        }

        // 1.5. 确保卡片预览覆盖层隐藏
        if (::cardPreviewOverlay.isInitialized && cardPreviewOverlay.visibility == View.VISIBLE) {
            cardPreviewOverlay.hide()
            Log.d(TAG, "卡片预览覆盖层已隐藏")
        }

        // 2. 检查WebView容器中的所有子视图，隐藏可能覆盖主页的视图
        val webViewContainer = findViewById<FrameLayout>(R.id.browser_webview_container)
        webViewContainer?.let { container ->
            Log.d(TAG, "WebView容器子视图数量: ${container.childCount}")

            for (i in 0 until container.childCount) {
                val child = container.getChildAt(i)
                Log.d(TAG, "子视图$i: ${child.javaClass.simpleName}, visibility: ${child.visibility}")

                // 如果是ViewPager2，在主页模式下隐藏它
                if (child.javaClass.simpleName.contains("ViewPager2")) {
                    Log.d(TAG, "发现ViewPager2，在主页模式下隐藏")
                    child.visibility = View.GONE
                }


            }
        }

        // 3. 确保主页内容容器没有被其他视图覆盖
        browserHomeContent.bringToFront()

        Log.d(TAG, "覆盖层清除完成")
    }

    /**
     * 显示浏览器主页
     */
    private fun showBrowserHome() {
        Log.d(TAG, "开始显示浏览器主页")

        // 强制清除所有可能的覆盖层
        hideAllOverlays()
        clearAllOverlays()

        // 显示主页内容
        browserHomeContent.visibility = View.VISIBLE
        browserHomeContent.isEnabled = true
        browserHomeContent.isClickable = true
        browserSearchInput.setText("")

        // 隐藏标签栏
        browserTabContainer.visibility = View.GONE

        // 隐藏四分之一圆弧操作栏（在主页不需要）
        quarterArcOperationBar?.hide()

        // 确保搜索引擎选择器完全可用
        browserSearchEngineSelector.visibility = View.VISIBLE
        browserSearchEngineSelector.isEnabled = true
        browserSearchEngineSelector.isClickable = true
        browserSearchEngineSelector.isFocusable = true
        browserSearchEngineSelector.alpha = 1.0f

        // 确保底部按钮也可用
        findViewById<com.google.android.material.button.MaterialButton>(R.id.browser_start_browsing_button)?.let { newBtn ->
            newBtn.visibility = View.VISIBLE
            newBtn.isEnabled = true
            newBtn.isClickable = true
            newBtn.alpha = 1.0f
        }

        findViewById<com.google.android.material.button.MaterialButton>(R.id.browser_preview_cards_button)?.let { cardsBtn ->
            cardsBtn.visibility = View.VISIBLE
            cardsBtn.isEnabled = true
            cardsBtn.isClickable = true
            cardsBtn.alpha = 1.0f
        }

        findViewById<com.google.android.material.button.MaterialButton>(R.id.browser_gesture_guide_button)?.let { gestureBtn ->
            gestureBtn.visibility = View.VISIBLE
            gestureBtn.isEnabled = true
            gestureBtn.isClickable = true
            gestureBtn.alpha = 1.0f
        }



        // 恢复默认搜索引擎图标
        updateSearchTabIcon()

        Log.d(TAG, "显示浏览器主页，搜索引擎选择器已启用")

        // 调试信息：检查各组件的状态
        Log.d(TAG, "主页组件状态检查:")
        Log.d(TAG, "- browserHomeContent.visibility: ${browserHomeContent.visibility}")
        Log.d(TAG, "- browserSearchEngineSelector.visibility: ${browserSearchEngineSelector.visibility}")
        Log.d(TAG, "- browserSearchEngineSelector.isEnabled: ${browserSearchEngineSelector.isEnabled}")
        Log.d(TAG, "- browserSearchEngineSelector.isClickable: ${browserSearchEngineSelector.isClickable}")

        // 检查底部按钮状态
        findViewById<com.google.android.material.button.MaterialButton>(R.id.browser_start_browsing_button)?.let { btn ->
            Log.d(TAG, "- 新建按钮状态: visible=${btn.visibility}, enabled=${btn.isEnabled}, clickable=${btn.isClickable}")
        }



        // 确保手势提示覆盖层不会干扰
        findViewById<FrameLayout>(R.id.browser_gesture_overlay)?.let { overlay ->
            overlay.visibility = View.GONE
            Log.d(TAG, "- 手势提示覆盖层已隐藏")
        }

        // 检查WebView容器的子视图，确保没有覆盖层
        val webViewContainer = findViewById<FrameLayout>(R.id.browser_webview_container)
        webViewContainer?.let { container ->
            Log.d(TAG, "WebView容器子视图数量: ${container.childCount}")
            for (i in 0 until container.childCount) {
                val child = container.getChildAt(i)
                Log.d(TAG, "- 子视图$i: ${child.javaClass.simpleName}, visibility=${child.visibility}, clickable=${child.isClickable}")


            }
        }

        // 最终测试：确保关键元素可以接收触摸事件
        testTouchability()
    }

    /**
     * 显示ViewPager2（WebView卡片容器）
     */
    private fun showViewPager2() {
        Log.d(TAG, "开始显示ViewPager2")

        val webViewContainer = findViewById<FrameLayout>(R.id.browser_webview_container)
        webViewContainer?.let { container ->
            Log.d(TAG, "WebView容器子视图数量: ${container.childCount}")

            for (i in 0 until container.childCount) {
                val child = container.getChildAt(i)
                Log.d(TAG, "子视图$i: ${child.javaClass.simpleName}, 当前visibility: ${child.visibility}")

                if (child.javaClass.simpleName.contains("ViewPager2")) {
                    val oldVisibility = child.visibility
                    child.visibility = View.VISIBLE
                    Log.d(TAG, "ViewPager2可见性: $oldVisibility -> ${child.visibility}")

                    // 确保ViewPager2在最前面
                    child.bringToFront()
                    Log.d(TAG, "ViewPager2已显示并置于最前面")
                    return
                }
            }
            Log.w(TAG, "未找到ViewPager2")
        } ?: run {
            Log.e(TAG, "WebView容器为null")
        }
    }

    /**
     * 测试关键元素的触摸能力
     */
    private fun testTouchability() {
        Log.d(TAG, "开始测试触摸能力")

        // 测试搜索引擎选择器
        Log.d(TAG, "搜索引擎选择器状态:")
        Log.d(TAG, "- visibility: ${browserSearchEngineSelector.visibility}")
        Log.d(TAG, "- isEnabled: ${browserSearchEngineSelector.isEnabled}")
        Log.d(TAG, "- isClickable: ${browserSearchEngineSelector.isClickable}")
        Log.d(TAG, "- isFocusable: ${browserSearchEngineSelector.isFocusable}")
        Log.d(TAG, "- alpha: ${browserSearchEngineSelector.alpha}")

        // 测试底部按钮
        findViewById<com.google.android.material.button.MaterialButton>(R.id.browser_start_browsing_button)?.let { btn ->
            Log.d(TAG, "新建按钮状态:")
            Log.d(TAG, "- visibility: ${btn.visibility}")
            Log.d(TAG, "- isEnabled: ${btn.isEnabled}")
            Log.d(TAG, "- isClickable: ${btn.isClickable}")
            Log.d(TAG, "- alpha: ${btn.alpha}")
        }

        // 测试主页内容容器
        Log.d(TAG, "主页内容容器状态:")
        Log.d(TAG, "- visibility: ${browserHomeContent.visibility}")
        Log.d(TAG, "- isEnabled: ${browserHomeContent.isEnabled}")
        Log.d(TAG, "- isClickable: ${browserHomeContent.isClickable}")

        Log.d(TAG, "触摸能力测试完成")
    }





    /**
     * 显示地址栏刷新动画
     */
    private fun showAddressBarRefreshAnimation() {
        // 地址栏滚动动画
        val currentText = browserSearchInput.text.toString()
        if (currentText.isNotEmpty()) {
            // 创建滚动动画
            val scrollAnimator = android.animation.ObjectAnimator.ofInt(
                browserSearchInput, "scrollX", 0, browserSearchInput.width / 2, 0
            ).apply {
                duration = 800
                interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            }

            // 创建透明度动画
            val alphaAnimator = android.animation.ObjectAnimator.ofFloat(
                browserSearchInput, "alpha", 1f, 0.7f, 1f
            ).apply {
                duration = 800
                interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            }

            // 组合动画
            android.animation.AnimatorSet().apply {
                playTogether(scrollAnimator, alphaAnimator)
                start()
            }
        }
    }

    // setupModeSwitchWidget方法已移除，因为ModeSwitchWidget已被禁用

    /**
     * 执行浏览器搜索 - 修复搜索关键词机制
     */
    private fun performBrowserSearch() {
        val query = browserSearchInput.text.toString().trim()
        if (query.isNotEmpty()) {
            try {
                // 增强的URL判断逻辑
                val isUrl = when {
                    // 1. 标准URL格式判断
                    android.webkit.URLUtil.isValidUrl(query) -> true
                    // 2. 包含域名但没有协议的情况（至少包含一个点且不包含空格）
                    query.contains(".") && !query.contains(" ") && query.count { it == '.' } >= 1 -> true
                    // 3. 明确的协议开头
                    query.startsWith("http://") || query.startsWith("https://") -> true
                    // 4. 本地文件
                    query.startsWith("file://") -> true
                    // 5. 其他协议
                    query.contains("://") -> true
                    // 6. IP地址格式
                    query.matches(Regex("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}(:\\d+)?(/.*)?$")) -> true
                    else -> false
                }

                val url = if (isUrl) {
                    // 如果是URL，确保有http/https前缀
                    when {
                        query.startsWith("http://") || query.startsWith("https://") || query.startsWith("file://") -> query
                        query.matches(Regex("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}.*")) -> "http://$query" // IP地址用http
                        else -> "https://$query"
                    }
                } else {
                    // 如果不是URL，使用当前搜索引擎搜索
                    val searchEngine = currentSearchEngine ?: com.example.aifloatingball.model.SearchEngine(
                        name = "Google",
                        displayName = "Google",
                        url = "https://www.google.com/search?q={query}",
                        iconResId = R.drawable.ic_google,
                        description = "Google搜索"
                    )
                    searchEngine.getSearchUrl(query)
                }

                // 加载URL到当前页面或新页面
                loadBrowserContent(url)

                Log.d(TAG, "浏览器搜索 - 输入: '$query', 是否URL: $isUrl, 搜索引擎: ${currentSearchEngine?.name}, 最终URL: $url")

            } catch (e: Exception) {
                Log.e(TAG, "浏览器搜索失败", e)
                Toast.makeText(this, "搜索失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 加载浏览器内容 - 多页面版本
     */
    private fun loadBrowserContent(url: String) {
        try {
            Log.d(TAG, "开始加载URL: $url")

            // 修改逻辑：总是在新卡片中打开链接
            loadUrlInNewCard(url)

            // 隐藏主页内容，显示手势卡片式WebView界面
            browserHomeContent.visibility = View.GONE
            browserTabContainer.visibility = View.GONE

            // 关键修复：确保ViewPager2可见
            showViewPager2()

            // 使用post确保UI状态在下一个循环中同步
            browserLayout.post {
                // 再次确保ViewPager2可见（防止时序问题）
                showViewPager2()
                Log.d(TAG, "UI状态已同步，ViewPager2确保可见")
            }

            Log.d(TAG, "已切换到WebView模式，ViewPager2已显示")

            Log.d(TAG, "显示手势卡片式WebView界面，当前卡片数: ${gestureCardWebViewManager?.getAllCards()?.size}")

        } catch (e: Exception) {
            Log.e(TAG, "加载内容失败", e)
            Toast.makeText(this, "无法加载页面: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 设置浏览器字母索引栏
     */
    private fun setupBrowserLetterIndexBar() {
        // 获取所有搜索引擎
        val allEngines = mutableListOf<com.example.aifloatingball.model.SearchEngine>()

        // 添加默认搜索引擎
        val defaultEngines = com.example.aifloatingball.model.SearchEngine.DEFAULT_ENGINES
        allEngines.addAll(defaultEngines)

        // 设置字母索引栏
        browserLetterIndexBar.engines = allEngines
        browserLetterIndexBar.onLetterSelectedListener = object : com.example.aifloatingball.view.LetterIndexBar.OnLetterSelectedListener {
            override fun onLetterSelected(view: View, letter: Char) {
                Log.d(TAG, "字母索引栏被点击，选择字母: $letter")
                updateBrowserEngineList(letter)
            }
        }
    }

    /**
     * 更新浏览器搜索引擎列表
     */
    private fun updateBrowserEngineList(letter: Char) {
        browserLetterTitle.text = if (letter == '#') "全部" else letter.toString()

        // 获取所有搜索引擎
        val allEngines = mutableListOf<com.example.aifloatingball.model.SearchEngine>()
        val defaultEngines = com.example.aifloatingball.model.SearchEngine.DEFAULT_ENGINES
        allEngines.addAll(defaultEngines)

        // 添加AI搜索引擎
        try {
            val aiEngines = com.example.aifloatingball.model.AISearchEngine.DEFAULT_AI_ENGINES.map { aiEngine ->
                com.example.aifloatingball.model.SearchEngine(
                    name = aiEngine.name,
                    displayName = aiEngine.name,
                    url = aiEngine.url,
                    iconResId = aiEngine.iconResId,
                    description = aiEngine.description,
                    searchUrl = aiEngine.searchUrl ?: aiEngine.url,
                    isAI = true
                )
            }
            allEngines.addAll(aiEngines)
        } catch (e: Exception) {
            Log.w(TAG, "无法加载AI搜索引擎: ${e.message}")
        }

        // 过滤搜索引擎
        val filteredEngines = if (letter == '#') {
            // 显示所有搜索引擎
            allEngines
        } else {
            // 过滤以指定字母开头的引擎
            allEngines.filter { engine ->
                engine.name.uppercase().startsWith(letter.uppercase())
            }
        }

        // 更新RecyclerView适配器
        if (::draggableEngineAdapter.isInitialized) {
            draggableEngineAdapter.updateEngines(filteredEngines)
        }

        Log.d(TAG, "更新搜索引擎列表: 字母=$letter, 总数=${allEngines.size}, 过滤后=${filteredEngines.size}")
    }

    /**
     * 创建浏览器搜索引擎视图
     */
    private fun createBrowserEngineView(engine: com.example.aifloatingball.model.SearchEngine): View {
        val engineView = layoutInflater.inflate(R.layout.item_search_engine_preview, null)

        val engineIcon = engineView.findViewById<ImageView>(R.id.engine_icon)
        val engineName = engineView.findViewById<TextView>(R.id.engine_name)
        val engineDescription = engineView.findViewById<TextView>(R.id.engine_description)

        // 设置引擎信息
        engineName.text = engine.displayName
        engineDescription.text = engine.description

        // 使用FaviconLoader加载搜索引擎图标
        com.example.aifloatingball.utils.FaviconLoader.loadIcon(
            engineIcon,
            engine.url,
            engine.iconResId
        )

        // 设置点击监听器
        engineView.setOnClickListener {
            // 切换当前搜索引擎
            currentSearchEngine = engine

            val query = browserSearchInput.text.toString().trim()

            // 检查输入是否为URL（避免将URL作为搜索关键词）
            val isUrl = query.isNotEmpty() && (
                query.startsWith("http://") ||
                query.startsWith("https://") ||
                query.contains("://") ||
                (query.contains(".") && !query.contains(" "))
            )

            val url = if (query.isNotEmpty() && !isUrl) {
                // 有查询内容且不是URL，使用搜索引擎搜索
                engine.getSearchUrl(query)
            } else {
                // 没有查询内容或输入的是URL，跳转到搜索引擎首页
                val baseUrl = engine.url.split("?")[0].replace("{query}", "")
                    .replace("search", "").replace("/s", "").replace("/search", "")
                if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
            }

            loadBrowserContent(url)
            browserLayout.closeDrawer(GravityCompat.START)

            // 更新搜索tab图标
            updateSearchTabIcon()

            Log.d(TAG, "选择搜索引擎: ${engine.name}, 查询: '$query', 是否URL: $isUrl, 最终URL: $url")
        }

        return engineView
    }

    /**
     * 在新卡片中加载URL
     */
    private fun loadUrlInNewCard(url: String, inBackground: Boolean = false) {
        Log.d(TAG, "在新卡片中加载URL: $url, 后台模式: $inBackground")

        try {
            var newCard: GestureCardWebViewManager.WebViewCardData? = null

            // 优先使用MobileCardManager
            if (mobileCardManager != null) {
                newCard = mobileCardManager?.addNewCard(url)
                Log.d(TAG, "使用MobileCardManager创建新卡片")
            } else {
                // 确保GestureCardWebViewManager已初始化
                if (gestureCardWebViewManager == null) {
                    setupBrowserWebView()
                }
                newCard = gestureCardWebViewManager?.addNewCard(url)
                Log.d(TAG, "使用GestureCardWebViewManager创建新卡片")
            }

            if (newCard != null) {
                Log.d(TAG, "新卡片创建成功: ${newCard.id}")

                if (!inBackground) {
                    // 前台模式：立即切换到新卡片
                    showWebViewMode()
                } else {
                    // 后台模式：不切换，保持当前状态
                    Toast.makeText(this, "已在后台打开新页面", Toast.LENGTH_SHORT).show()
                }
            } else {
                Log.e(TAG, "创建新卡片失败")
                Toast.makeText(this, "无法打开新页面", Toast.LENGTH_SHORT).show()
            }

        } catch (e: Exception) {
            Log.e(TAG, "在新卡片中加载URL时发生错误", e)
            Toast.makeText(this, "打开页面失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 在当前卡片中加载URL
     */
    private fun loadUrlInCurrentCard(url: String) {
        Log.d(TAG, "在当前卡片中加载URL: $url")

        try {
            var loaded = false

            // 优先使用MobileCardManager
            if (mobileCardManager != null) {
                mobileCardManager?.loadUrl(url)
                loaded = true
                Log.d(TAG, "使用MobileCardManager在当前卡片中加载URL")
            } else if (gestureCardWebViewManager != null) {
                gestureCardWebViewManager?.loadUrl(url)
                loaded = true
                Log.d(TAG, "使用GestureCardWebViewManager在当前卡片中加载URL")
            }

            if (loaded) {
                showWebViewMode()
            } else {
                // 如果没有当前卡片，创建新卡片
                loadUrlInNewCard(url)
            }

        } catch (e: Exception) {
            Log.e(TAG, "在当前卡片中加载URL时发生错误", e)
            // 回退到新建卡片
            loadUrlInNewCard(url)
        }
    }

    /**
     * 显示WebView模式
     */
    private fun showWebViewMode() {
        // 隐藏主页内容，显示手势卡片式WebView界面
        browserHomeContent.visibility = View.GONE
        browserTabContainer.visibility = View.GONE

        // 显示ViewPager2
        showViewPager2()

        // 显示四分之一圆弧操作栏（在WebView模式下需要）
        quarterArcOperationBar?.show()

        Log.d(TAG, "已切换到WebView模式")
    }

    /**
     * 强制刷新UI状态，确保所有组件都处于正确状态
     */
    private fun forceRefreshUIState() {
        // 检查两种卡片管理器是否有卡片
        val hasGestureCards = gestureCardWebViewManager?.getAllCards()?.isNotEmpty() == true
        val hasMobileCards = mobileCardManager?.getAllCards()?.isNotEmpty() == true
        val hasCards = hasGestureCards || hasMobileCards

        Log.d(TAG, "强制刷新UI状态，手势卡片: $hasGestureCards, 手机卡片: $hasMobileCards, 总计有卡片: $hasCards")

        if (hasCards) {
            // WebView模式：显示卡片，隐藏主页
            browserHomeContent.visibility = View.GONE
            browserTabContainer.visibility = View.GONE

            // 显示ViewPager2
            showViewPager2()

            // 显示四分之一圆弧操作栏
            quarterArcOperationBar?.show()

            Log.d(TAG, "切换到WebView模式")
        } else {
            // 主页模式：隐藏卡片，显示主页
            showBrowserHome()
            Log.d(TAG, "切换到主页模式")
        }
    }

    /**
     * 重写触摸事件分发，确保抽屉打开时优先处理抽屉的触摸事件
     */
    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev == null) return super.dispatchTouchEvent(ev)

        // 如果浏览器抽屉已经打开，优先让抽屉处理触摸事件
        if (::browserLayout.isInitialized &&
            (browserLayout.isDrawerOpen(GravityCompat.START) || browserLayout.isDrawerOpen(GravityCompat.END))) {
            Log.d(TAG, "抽屉已打开，传递触摸事件给抽屉处理")
            return super.dispatchTouchEvent(ev)
        }

        return super.dispatchTouchEvent(ev)
    }



    /**
     * 处理权限请求结果
     */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            1001 -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // 权限授予后重新检测语音支持情况
                    detectAndUpdateVoiceSupport()
                    startVoiceRecognition()
                } else {
                    voiceStatusText.text = "需要录音权限才能使用语音识别"
                    // 权限被拒绝后也重新检测，可能有其他不需要权限的方案
                    detectAndUpdateVoiceSupport()
                }
            }
        }
    }

    /**
     * dp转px的扩展函数
     */
    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    /**
     * 设置对话功能
     */
    private fun setupChat() {
        try {
            // 初始化对话相关的UI组件
            val chatSearchInput = findViewById<EditText>(R.id.chat_search_input)
            val chatAddContactButton = findViewById<ImageButton>(R.id.chat_add_contact_button)
            val chatTabLayout = findViewById<com.google.android.material.tabs.TabLayout>(R.id.chat_tab_layout)
            val chatContactsRecyclerView = findViewById<RecyclerView>(R.id.chat_contacts_recycler_view)

            // 初始化联系人适配器
            val chatContactAdapter = ChatContactAdapter(
                onContactClick = { contact ->
                    // 处理联系人点击事件
                    openChatWithContact(contact)
                },
                onContactLongClick = { contact ->
                    // 处理联系人长按事件
                    Log.d(TAG, "SimpleModeActivity收到长按事件: ${contact.name}")
                    showContactOptionsDialog(contact)
                    true
                },
                onContactDoubleClick = { contact ->
                    // 处理联系人双击事件 - 添加到当前标签页
                    addContactToCurrentTab(contact)
                    true
                },
                onCategoryLongClick = { category ->
                    // 只有在用户明确长按分组标题时才显示管理选项
                    // 避免意外触发管理菜单
                    if (category.name != "全部" && category.name != "提示") {
                        showCategoryManagementDialog(category)
                    } else {
                        Log.d(TAG, "跳过系统分组的长按管理: ${category.name}")
                    }
                    true
                },
                getContactGroup = { contact ->
                    // 获取联系人所在的分组名称
                    findContactGroup(contact)
                }
            )

            // 设置RecyclerView
            chatContactsRecyclerView?.apply {
                layoutManager = LinearLayoutManager(this@SimpleModeActivity)
                adapter = chatContactAdapter
            }

            // 保存适配器引用
            this.chatContactAdapter = chatContactAdapter

            // 设置搜索功能 - 始终针对所有AI助手提问
            chatSearchInput?.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                    val query = chatSearchInput.text.toString().trim()
                    if (query.isNotEmpty()) {
                        // 直接启动全局AI搜索，向所有AI助手提问
                        startGlobalAISearch(query)
                        // 清空搜索框
                        chatSearchInput.text.clear()
                        // 隐藏输入法
                        hideKeyboard(chatSearchInput)
                    }
                    true
                } else {
                    false
                }
            }

            // 设置输入框点击监听，只在用户主动点击时才激活
            chatSearchInput?.setOnClickListener { view ->
                // 用户主动点击时，启用焦点并显示键盘
                view.isFocusable = true
                view.isFocusableInTouchMode = true
                view.requestFocus()
                showKeyboard(view as EditText)
            }

            // 设置输入框焦点监听
            chatSearchInput?.setOnFocusChangeListener { view, hasFocus ->
                if (!hasFocus) {
                    // 失去焦点时隐藏输入法并禁用焦点
                    hideKeyboard(view)
                    view.isFocusable = false
                    view.isFocusableInTouchMode = false
                }
            }

            // 添加回车键监听
            chatSearchInput?.setOnKeyListener { view, keyCode, event ->
                if (keyCode == android.view.KeyEvent.KEYCODE_ENTER && event.action == android.view.KeyEvent.ACTION_DOWN) {
                    val query = chatSearchInput.text.toString().trim()
                    if (query.isNotEmpty()) {
                        // 直接启动全局AI搜索，向所有AI助手提问
                        startGlobalAISearch(query)
                        // 清空搜索框
                        chatSearchInput.text.clear()
                        // 隐藏输入法并清除焦点
                        hideKeyboard(view)
                        view.clearFocus()
                    }
                    true
                } else {
                    false
                }
            }

            // 设置添加联系人按钮（右上角+号）
            chatAddContactButton?.setOnClickListener {
                // 打开AI联系人列表界面
                openAIContactListActivity()
            }

            // 先加载联系人数据
            loadInitialContacts()

            // 设置TabLayout - 重新设计逻辑
            chatTabLayout?.apply {
                // 加载保存的标签顺序，如果没有则使用默认顺序
                loadTabsInOrder()

                // 添加"+"按钮用于创建新分组
                addTab(newTab().setText("+").setIcon(R.drawable.ic_add))

                addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
                    override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                        val newPosition = tab?.position ?: return
                        val chatContactsRecyclerView = findViewById<RecyclerView>(R.id.chat_contacts_recycler_view)

                        // 当切换tab时，隐藏悬浮卡片
                        stackedCardPreview?.let {
                            if (it.visibility == View.VISIBLE) {
                                it.visibility = View.GONE
                            }
                        }

                        // 检查必要的组件是否存在
                        if (chatContactsRecyclerView == null || chatTabLayout == null) {
                            // 如果组件不存在，直接执行内容切换，不使用动画
                            executeTabContentSwitch(newPosition, tab)
                            currentTabPosition = newPosition
                            return
                        }

                        // 执行标签切换动画
                        tabSwitchAnimationManager.animateTabSwitch(
                            recyclerView = chatContactsRecyclerView,
                            tabLayout = chatTabLayout!!,
                            fromPosition = currentTabPosition,
                            toPosition = newPosition
                        ) {
                            // 在动画回调中执行实际的内容切换
                            executeTabContentSwitch(newPosition, tab)
                        }

                        // 更新当前标签位置
                        currentTabPosition = newPosition
                    }

                    override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
                    override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                        try {
                            // 只有在标签页初始化完成且不是拖拽状态时才处理双击管理
                            if (!isTabsInitialized || isDraggingTabs) {
                                Log.d(TAG, "标签页未初始化完成或正在拖拽，跳过双击管理")
                                return
                            }

                            // 双击标签页进行管理
                            val tabText = tab?.text?.toString() ?: return

                            when (tabText) {
                                "全部" -> {
                                    // "全部"标签 - 显示聚合视图的管理选项
                                    showAllTabManagement()
                                }
                                "AI助手" -> {
                                    // "AI助手"标签 - 可以重命名和删除
                                    showAIAssistantGroupManagement(tab)
                                }
                                "+" -> {
                                    // +号按钮 - 不做任何操作
                                    Log.d(TAG, "+号按钮被重新选择")
                                }
                                else -> {
                                    // 自定义分组 - 可以重命名、删除
                                    showCustomGroupManagement(tab)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "双击标签页管理失败", e)
                        }
                    }
                })

                // 设置标签页拖动排序功能
                setupTabDragAndDrop(chatTabLayout)

                // 默认选中"全部"标签页并显示内容
                val allTab = chatTabLayout?.getTabAt(0)
                allTab?.select()

                // 延迟一点时间确保数据加载完成后再显示
                Handler(Looper.getMainLooper()).postDelayed({
                    showAllUserAIContacts()
                    // 标记标签页初始化完成
                    isTabsInitialized = true
                    Log.d(TAG, "标签页初始化完成")
                }, 100)
            }

            Log.d(TAG, "对话功能初始化完成")
        } catch (e: Exception) {
            Log.e(TAG, "设置对话功能失败", e)
        }
    }

    /**
     * 设置API密钥同步广播接收器
     */
    private fun setupApiKeySyncReceiver() {
        try {
            apiKeySyncReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    try {
                        when (intent?.action) {
                            "com.example.aifloatingball.SETTINGS_API_KEY_UPDATED" -> {
                                val serviceName = intent.getStringExtra("service_name") ?: return
                                val apiKey = intent.getStringExtra("api_key") ?: return

                                Log.d(TAG, "收到设置同步广播: $serviceName")

                                // 更新联系人列表中的API密钥
                                updateExistingContactApiKey(serviceName, apiKey)

                                // 如果该AI还不存在，自动添加它
                                addAIIfNotExists(serviceName, apiKey)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "处理API密钥同步广播失败", e)
                    }
                }
            }

            // 注册广播接收器
            val filter = IntentFilter().apply {
                addAction("com.example.aifloatingball.SETTINGS_API_KEY_UPDATED")
            }
            registerReceiver(apiKeySyncReceiver, filter)

            Log.d(TAG, "API密钥同步广播接收器已注册")

        } catch (e: Exception) {
            Log.e(TAG, "设置API密钥同步广播接收器失败", e)
        }
    }

    /**
     * 如果AI不存在则自动添加
     */
    private fun addAIIfNotExists(serviceName: String, apiKey: String) {
        try {
            // 检查是否已存在该AI
            var exists = false
            for (category in allContacts) {
                for (contact in category.contacts) {
                    if (contact.name.equals(serviceName, ignoreCase = true)) {
                        exists = true
                        break
                    }
                }
                if (exists) break
            }

            if (!exists && apiKey.isNotBlank()) {
                // 自动添加该AI
                val description = when (serviceName.lowercase()) {
                    "deepseek" -> "🚀 DeepSeek - 性能强劲，支持中文"
                    "智谱ai" -> "🧠 智谱AI - GLM-4大语言模型"
                    "chatgpt" -> "🤖 ChatGPT - OpenAI的经典模型"
                    "claude" -> "💡 Claude - Anthropic的智能助手"
                    "gemini" -> "🌟 Gemini - Google的AI助手"
                    "kimi" -> "🌙 Kimi - Moonshot的长文本专家"
                    "文心一言" -> "📚 文心一言 - 百度的大语言模型"
                    "通义千问" -> "🎯 通义千问 - 阿里巴巴的AI"
                    "讯飞星火" -> "⚡ 讯飞星火 - 科大讯飞的AI"
                    else -> "${serviceName}AI助手"
                }

                addAIContactToCategory(serviceName, description, apiKey)

                runOnUiThread {
                    Toast.makeText(this, "${serviceName}已自动添加到对话列表", Toast.LENGTH_SHORT).show()
                }

                Log.d(TAG, "从设置自动添加AI: $serviceName")
            }

        } catch (e: Exception) {
            Log.e(TAG, "自动添加AI失败", e)
        }
    }

    /**
     * 执行对话搜索
     */
    private fun performChatSearch(query: String) {
        try {
            if (query.trim().isEmpty()) {
                // 如果搜索内容为空，显示所有联系人（不进行AI搜索）
                chatContactAdapter?.searchContacts("")
                return
            }

            // 对话tab的搜索框始终针对所有AI助手提问，而不是过滤AI名称
            // 启动全局搜索（向所有AI提问）
            startGlobalAISearch(query)

            Log.d(TAG, "执行对话搜索: $query")
        } catch (e: Exception) {
            Log.e(TAG, "搜索失败", e)
        }
    }

    /**
     * 启动AI分组搜索（根据当前标签页向对应AI提问）
     */
    private fun startGlobalAISearch(query: String) {
        try {
            // 获取当前选中的标签页
            val chatTabLayout = findViewById<com.google.android.material.tabs.TabLayout>(R.id.chat_tab_layout)
            val currentTabPosition = chatTabLayout?.selectedTabPosition ?: 0
            val currentTabName = chatTabLayout?.getTabAt(currentTabPosition)?.text?.toString()

            // 根据当前标签页获取对应的AI列表
            val availableAIs = getAIsForCurrentTab(currentTabPosition, currentTabName)

            Log.d(TAG, "当前标签页AI搜索: 标签页=$currentTabPosition, 名称=$currentTabName, AI数量=${availableAIs.size}")

            if (availableAIs.isEmpty()) {
                val message = when (currentTabPosition) {
                    0 -> "没有找到已配置的AI助手，请先添加并配置AI助手"
                    1 -> "AI助手分组为空，请将AI移动到此分组"
                    else -> {
                        if (currentTabName != null && currentTabName != "+") {
                            "分组 \"$currentTabName\" 中没有AI助手，请添加AI到此分组"
                        } else {
                            "请先配置AI助手的API密钥"
                        }
                    }
                }
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                return
            }

            // 显示搜索范围提示
            val searchScope = when (currentTabPosition) {
                0 -> "所有AI助手"
                1 -> "AI助手分组"
                else -> {
                    if (currentTabName != null && currentTabName != "+") {
                        "分组 \"$currentTabName\""
                    } else {
                        "当前分组"
                    }
                }
            }
            Toast.makeText(this, "正在${searchScope}中搜索：$query", Toast.LENGTH_SHORT).show()

            // 创建或打开全局搜索对话
            openGlobalSearchChat(query, availableAIs)

        } catch (e: Exception) {
            Log.e(TAG, "启动全局AI搜索失败", e)
            Toast.makeText(this, "搜索失败，请重试", Toast.LENGTH_SHORT).show()
        }
    }



    /**
     * 获取置顶的可用的AI助手
     */
    private fun getPinnedAvailableAIs(): List<ChatContact> {
        val availableAIs = mutableListOf<ChatContact>()
        Log.d(TAG, "getPinnedAvailableAIs: 开始查找可用AI，总分类数: ${allContacts.size}")
        
        allContacts.forEach { category ->
            Log.d(TAG, "getPinnedAvailableAIs: 检查分类: ${category.name}, 联系人数: ${category.contacts.size}")
            if (category.name == "AI助手") {
                Log.d(TAG, "getPinnedAvailableAIs: 找到AI助手分类，联系人数: ${category.contacts.size}")
                category.contacts.forEach { contact ->
                    Log.d(TAG, "getPinnedAvailableAIs: 检查AI: ${contact.name}, isPinned: ${contact.isPinned}")
                    val isPinned = isPinnedAIContact(contact)
                    val hasValidKey = hasValidApiKey(contact)
                    Log.d(TAG, "getPinnedAvailableAIs: AI ${contact.name} - isPinnedAIContact: $isPinned, hasValidApiKey: $hasValidKey")
                    
                    // 检查是否为置顶的AI联系人（通过名称或其他标识）
                    if (isPinned && hasValidKey) {
                        availableAIs.add(contact)
                        Log.d(TAG, "getPinnedAvailableAIs: 添加可用AI: ${contact.name}")
                    } else {
                        Log.d(TAG, "getPinnedAvailableAIs: 跳过AI: ${contact.name} (isPinned: $isPinned, hasValidKey: $hasValidKey)")
                    }
                }
            }
        }
        
        Log.d(TAG, "getPinnedAvailableAIs: 最终可用AI数量: ${availableAIs.size}")
        availableAIs.forEach { ai ->
            Log.d(TAG, "getPinnedAvailableAIs: 可用AI: ${ai.name}")
        }
        
        return availableAIs
    }

    /**
     * 判断是否为置顶的AI联系人
     */
    private fun isPinnedAIContact(contact: ChatContact): Boolean {
        // 这里可以根据实际需求来判断哪些AI是置顶的
        // 暂时返回true，表示所有AI都是可用的
        return true
    }

    /**
     * 执行全局搜索 - 直接开始搜索，不显示弹窗
     */
    private fun openGlobalSearchChat(query: String, availableAIs: List<ChatContact>) {
        try {
            if (availableAIs.isEmpty()) {
                Toast.makeText(this, "没有找到已配置的AI助手", Toast.LENGTH_SHORT).show()
                return
            }

            // 直接开始全局搜索，不显示弹窗
            startGlobalSearchDirectly(query, availableAIs)

        } catch (e: Exception) {
            Log.e(TAG, "执行全局搜索失败", e)
            Toast.makeText(this, "搜索失败，请重试", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 显示全局搜索进度对话框
     */
    private fun showGlobalSearchProgressDialog(query: String, availableAIs: List<ChatContact>) {
        try {
            // 创建自定义布局
            val dialogLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(40, 30, 40, 30)
            }

            // 标题
            val titleText = TextView(this).apply {
                text = "🔍 全局AI搜索进度"
                textSize = 18f
                setTextColor(getColor(R.color.simple_mode_text_primary_light))
                setPadding(0, 0, 0, 20)
                gravity = android.view.Gravity.CENTER
            }
            dialogLayout.addView(titleText)

            // 问题显示
            val questionText = TextView(this).apply {
                text = "问题：\"$query\""
                textSize = 14f
                setTextColor(getColor(R.color.simple_mode_text_primary_light))
                setPadding(20, 10, 20, 10)
                background = getDrawable(R.drawable.search_box_background)
            }
            dialogLayout.addView(questionText)

            // 间距
            val spacer = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, 20)
            }
            dialogLayout.addView(spacer)

            // AI进度列表容器
            val progressContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }
            dialogLayout.addView(progressContainer)

            // 为每个AI创建进度卡片
            val progressCards = mutableMapOf<String, LinearLayout>()
            availableAIs.forEach { aiContact ->
                val progressCard = createAIProgressCard(aiContact, progressContainer)
                progressCards[aiContact.id] = progressCard
            }

            // 创建对话框
            val dialog = AlertDialog.Builder(this)
                .setView(dialogLayout)
                .setNegativeButton("关闭", null)
                .create()

            dialog.show()

            // 开始向所有AI发送查询
            startGlobalSearch(query, availableAIs, progressCards, dialog)

        } catch (e: Exception) {
            Log.e(TAG, "显示全局搜索进度对话框失败", e)
        }
    }

    /**
     * 创建AI进度卡片
     */
    private fun createAIProgressCard(aiContact: ChatContact, container: LinearLayout): LinearLayout {
        val cardLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(15, 15, 15, 15)
            background = getDrawable(R.drawable.search_box_background)
            val layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams.setMargins(0, 0, 0, 10)
            setLayoutParams(layoutParams)
        }

        // AI头像和名称
        val aiNameText = TextView(this).apply {
            text = aiContact.name
            textSize = 14f
            setTextColor(getColor(R.color.simple_mode_text_primary_light))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        cardLayout.addView(aiNameText)

        // 状态指示
        val statusText = TextView(this).apply {
            text = "⏳ 准备中..."
            textSize = 12f
            setTextColor(getColor(R.color.simple_mode_text_secondary_light))
            gravity = android.view.Gravity.END
        }
        cardLayout.addView(statusText)

        // 点击查看对话
        cardLayout.setOnClickListener {
            val intent = Intent(this@SimpleModeActivity, ChatActivity::class.java).apply {
                putExtra(ChatActivity.EXTRA_CONTACT, aiContact)
            }
            startActivity(intent)
        }

        container.addView(cardLayout)
        return cardLayout
    }

    /**
     * 直接开始全局搜索，不显示弹窗
     */
    private fun startGlobalSearchDirectly(query: String, availableAIs: List<ChatContact>) {
        try {
            availableAIs.forEach { aiContact ->
                lifecycleScope.launch {
                    try {
                        // 在后台向AI发送消息
                        sendMessageToAIInBackground(aiContact, query)
                    } catch (e: Exception) {
                        Log.e(TAG, "向AI ${aiContact.name} 发送消息失败", e)
                    }
                }
            }

            // 显示简单的提示
            Toast.makeText(this, "已向 ${availableAIs.size} 个AI助手发送问题，请查看各AI的回复", Toast.LENGTH_LONG).show()

        } catch (e: Exception) {
            Log.e(TAG, "开始全局搜索失败", e)
        }
    }

    /**
     * 开始全局搜索（带弹窗版本）
     */
    private fun startGlobalSearch(query: String, availableAIs: List<ChatContact>,
                                 progressCards: Map<String, LinearLayout>, dialog: AlertDialog) {
        try {
            var completedCount = 0
            val totalCount = availableAIs.size

            availableAIs.forEach { aiContact ->
                lifecycleScope.launch {
                    try {
                        // 更新状态：发送中
                        runOnUiThread {
                            updateProgressCard(progressCards[aiContact.id], "🚀 发送中...")
                        }

                        // 向AI的对话中发送消息（后台处理）
                        sendMessageToAIInBackground(aiContact, query)

                        // 模拟一个短暂延迟
                        delay(1000)

                        // 更新状态：已发送
                        runOnUiThread {
                            updateProgressCard(progressCards[aiContact.id], "✅ 已发送，点击查看回复")
                        }

                    } catch (e: Exception) {
                        runOnUiThread {
                            updateProgressCard(progressCards[aiContact.id], "❌ 发送失败")
                        }
                    }

                    completedCount++
                    if (completedCount >= totalCount) {
                        runOnUiThread {
                            // 所有AI都已发送完成
                            dialog.setTitle("🎉 搜索完成 - 点击AI查看回复")
                        }
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "开始全局搜索失败", e)
        }
    }

    /**
     * 更新进度卡片状态
     */
    private fun updateProgressCard(cardLayout: LinearLayout?, status: String) {
        try {
            cardLayout?.let { card ->
                val statusText = card.getChildAt(1) as TextView
                statusText.text = status
            }
        } catch (e: Exception) {
            Log.e(TAG, "更新进度卡片失败", e)
        }
    }

    /**
     * 在后台向AI发送消息
     */
    private suspend fun sendMessageToAIInBackground(aiContact: ChatContact, query: String) {
        withContext(Dispatchers.IO) {
            try {
                // 获取AI服务类型
                val serviceType = getAIServiceType(aiContact)
                if (serviceType != null) {
                    // 检查是否有API密钥配置
                    val apiKey = getApiKeyForService(serviceType)
                    if (apiKey.isNotBlank()) {
                        // 准备对话历史（这里可以加载已有的聊天记录）
                        val conversationHistory = listOf(
                            mapOf("role" to "user", "content" to query)
                        )

                        // 调用AI API发送消息
                        val response = sendMessageToAI(serviceType, query, conversationHistory, apiKey)

                        // 使用统一的ChatDataManager保存聊天记录
                        val chatDataManager = com.example.aifloatingball.data.ChatDataManager.getInstance(this@SimpleModeActivity)

                        // 获取或创建会话ID（使用AI联系人ID作为会话标识）
                        val sessionId = aiContact.id
                        chatDataManager.setCurrentSessionId(sessionId, serviceType)

                        // 添加用户消息和AI回复
                        chatDataManager.addMessage(sessionId, "user", query, serviceType)
                        chatDataManager.addMessage(sessionId, "assistant", response, serviceType)

                        // 更新联系人的最后消息
                        runOnUiThread {
                            updateContactLastMessage(aiContact, response)
                        }
                    } else {
                        // 没有API密钥，使用模拟回复
                        val mockResponse = generateMockResponse(aiContact.name, query)
                        runOnUiThread {
                            updateContactLastMessage(aiContact, mockResponse)
                        }
                    }
                } else {
                    // 无法识别AI服务类型，使用模拟回复
                    val mockResponse = generateMockResponse(aiContact.name, query)
                    runOnUiThread {
                        updateContactLastMessage(aiContact, mockResponse)
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "后台发送消息到${aiContact.name}失败", e)
                // 发生错误时，使用模拟回复
                val mockResponse = "抱歉，我暂时无法回答您的问题。请稍后再试或检查网络连接。"
                runOnUiThread {
                    updateContactLastMessage(aiContact, mockResponse)
                }
            }
        }
    }

    /**
     * 生成模拟回复
     */
    private fun generateMockResponse(aiName: String, query: String): String {
        val responses = mapOf(
            "DeepSeek" to "基于深度学习技术，我认为这个问题可以从以下几个角度来分析：1) 技术层面... 2) 实践层面... 3) 应用层面...",
            "智谱AI" to "作为GLM-4模型，我建议从理论和实践两个层面来理解这个问题。首先从理论基础说起...",
            "ChatGPT" to "根据我的训练数据和理解，这个问题的核心在于... 让我为您详细分析一下相关要点...",
            "Claude" to "我很乐意帮助您分析这个问题。让我从不同维度来看：安全性、实用性、可行性...",
            "Gemini" to "这是一个很有趣的问题！让我来为您详细分析一下各个方面的考量因素...",
            "Kimi" to "利用我的长文本处理能力，我可以为您提供全面的解答。首先我们来看问题的背景...",
            "文心一言" to "从中文语境的角度来看，这个问题涉及到多个层面的考虑，包括文化、技术、实践等方面...",
            "通义千问" to "综合多方面信息，我的分析如下：这个问题需要从系统性的角度来思考...",
            "讯飞星火" to "结合语音和文本理解技术，我认为这个问题的解决需要综合考虑多个因素..."
        )

        return responses[aiName] ?: "感谢您的提问！针对\"$query\"这个问题，我正在为您准备详细的回答。基于我的理解，这涉及多个方面的考虑..."
    }

    /**
     * 从聊天历史中获取最后的消息
     */
    private fun getLastChatMessageFromHistory(aiName: String): String {
        try {
            val chatDataManager = com.example.aifloatingball.data.ChatDataManager.getInstance(this)
            
            // 使用与灵动岛相同的ID生成逻辑
            val processedName = if (aiName.contains(Regex("[\\u4e00-\\u9fff]"))) {
                // 包含中文字符，直接使用原名称
                aiName
            } else {
                // 英文字符，转换为小写
                aiName.lowercase()
            }
            val contactId = "ai_${processedName.replace(" ", "_")}"
            
            Log.d(TAG, "简易模式获取历史消息 - AI名称: $aiName, 联系人ID: $contactId")
            
            // 获取对应的AI服务类型
            val serviceType = getAIServiceTypeFromName(aiName)
            if (serviceType != null) {
                Log.d(TAG, "简易模式获取历史消息 - 服务类型: ${serviceType.name}")
                
                // 先尝试从内存中获取
                var messages = chatDataManager.getMessages(contactId, serviceType)
                Log.d(TAG, "简易模式获取历史消息 - 从内存获取到 ${messages.size} 条消息")
                
                // 如果内存中没有数据，尝试重新加载数据
                if (messages.isEmpty()) {
                    Log.d(TAG, "简易模式获取历史消息 - 内存中无数据，尝试重新加载")
                    // 这里可以添加重新加载逻辑，但ChatDataManager应该已经自动加载了
                }
                
                if (messages.isNotEmpty()) {
                    val lastMessage = messages.last()
                    val result = lastMessage.content.take(50) + if (lastMessage.content.length > 50) "..." else ""
                    Log.d(TAG, "简易模式获取历史消息 - 最后消息: ${result.take(20)}...")
                    return result
                } else {
                    Log.d(TAG, "简易模式获取历史消息 - 该AI暂无历史消息")
                }
            } else {
                Log.w(TAG, "简易模式获取历史消息 - 无法识别AI服务类型: $aiName")
            }
            
            return ""
        } catch (e: Exception) {
            Log.e(TAG, "获取最后聊天消息失败", e)
            return ""
        }
    }
    
    /**
     * 从聊天历史中获取最后的时间
     */
    private fun getLastChatTimeFromHistory(aiName: String): Long {
        try {
            val chatDataManager = com.example.aifloatingball.data.ChatDataManager.getInstance(this)
            
            // 使用与灵动岛相同的ID生成逻辑
            val processedName = if (aiName.contains(Regex("[\\u4e00-\\u9fff]"))) {
                // 包含中文字符，直接使用原名称
                aiName
            } else {
                // 英文字符，转换为小写
                aiName.lowercase()
            }
            val contactId = "ai_${processedName.replace(" ", "_")}"
            
            // 获取对应的AI服务类型
            val serviceType = getAIServiceTypeFromName(aiName)
            if (serviceType != null) {
                val messages = chatDataManager.getMessages(contactId, serviceType)
                if (messages.isNotEmpty()) {
                    return messages.last().timestamp
                }
            }
            
            return 0
        } catch (e: Exception) {
            Log.e(TAG, "获取最后聊天时间失败", e)
            return 0
        }
    }
    
    /**
     * 根据AI名称获取对应的AIServiceType
     */
    private fun getAIServiceTypeFromName(aiName: String): AIServiceType? {
        return when (aiName) {
            "DeepSeek" -> AIServiceType.DEEPSEEK
            "ChatGPT" -> AIServiceType.CHATGPT
            "Claude" -> AIServiceType.CLAUDE
            "Gemini" -> AIServiceType.GEMINI
            "智谱AI" -> AIServiceType.ZHIPU_AI
            "文心一言" -> AIServiceType.WENXIN
            "通义千问" -> AIServiceType.QIANWEN
            "讯飞星火" -> AIServiceType.XINGHUO
            "Kimi" -> AIServiceType.KIMI
            else -> null
        }
    }
    
    /**
     * 调试方法：检查所有AI的数据状态
     */
    private fun debugAllAIData() {
        try {
            val chatDataManager = com.example.aifloatingball.data.ChatDataManager.getInstance(this)
            val aiNames = listOf("DeepSeek", "Kimi", "智谱AI", "ChatGPT", "Claude", "Gemini")
            
            Log.d(TAG, "=== 调试所有AI数据状态 ===")
            
            aiNames.forEach { aiName ->
                val serviceType = getAIServiceTypeFromName(aiName)
                if (serviceType != null) {
                    val processedName = if (aiName.contains(Regex("[\\u4e00-\\u9fff]"))) {
                        aiName
                    } else {
                        aiName.lowercase()
                    }
                    val contactId = "ai_${processedName.replace(" ", "_")}"
                    
                    val messages = chatDataManager.getMessages(contactId, serviceType)
                    Log.d(TAG, "AI: $aiName, ID: $contactId, 服务类型: ${serviceType.name}, 消息数: ${messages.size}")
                    
                    if (messages.isNotEmpty()) {
                        val lastMessage = messages.last()
                        Log.d(TAG, "  最后消息: ${lastMessage.content.take(30)}... (${lastMessage.role})")
                        
                        // 显示所有消息的详细信息
                        messages.forEachIndexed { index, message ->
                            Log.d(TAG, "    消息${index + 1}: [${message.role}] ${message.content.take(50)}...")
                        }
                    }
                } else {
                    Log.w(TAG, "AI: $aiName - 无法识别服务类型")
                }
            }
            
            Log.d(TAG, "=== 调试完成 ===")
        } catch (e: Exception) {
            Log.e(TAG, "调试AI数据状态失败", e)
        }
    }
    
    /**
     * 强制刷新AI数据
     */
    private fun forceRefreshAIData() {
        try {
            val chatDataManager = com.example.aifloatingball.data.ChatDataManager.getInstance(this)
            
            // 强制重新加载所有数据
            Log.d(TAG, "强制刷新AI数据 - 重新加载所有数据")
            chatDataManager.forceReloadAllData()
            
            // 调试数据状态
            chatDataManager.debugAllData()
            
            // 刷新联系人列表
            runOnUiThread {
                chatContactAdapter?.notifyDataSetChanged()
                Log.d(TAG, "强制刷新AI数据 - UI已刷新")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "强制刷新AI数据失败", e)
        }
    }
    
    /**
     * AI对话更新广播接收器
     */
    private val aiChatUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.example.aifloatingball.AI_CHAT_UPDATED") {
                val serviceTypeName = intent.getStringExtra("ai_service_type")
                val sessionId = intent.getStringExtra("session_id")
                val messageCount = intent.getIntExtra("message_count", 0)
                val lastMessage = intent.getStringExtra("last_message")
                val timestamp = intent.getLongExtra("timestamp", 0)
                
                Log.d(TAG, "收到AI对话更新广播:")
                Log.d(TAG, "  服务类型: $serviceTypeName")
                Log.d(TAG, "  会话ID: $sessionId")
                Log.d(TAG, "  消息数: $messageCount")
                Log.d(TAG, "  最后消息: ${lastMessage?.take(30)}...")
                Log.d(TAG, "  时间戳: $timestamp")
                
                // 强制刷新AI对话数据
                refreshAIContactData()
            }
        }
    }
    
    /**
     * 设置AI对话更新广播接收器
     */
    private fun setupAIChatUpdateReceiver() {
        try {
            val filter = IntentFilter("com.example.aifloatingball.AI_CHAT_UPDATED")
            registerReceiver(aiChatUpdateReceiver, filter)
            Log.d(TAG, "AI对话更新广播接收器已注册")
        } catch (e: Exception) {
            Log.e(TAG, "注册AI对话更新广播接收器失败", e)
        }
    }
    
    /**
     * 刷新AI对话数据
     */
    private fun refreshAIContactData() {
        try {
            // 重新加载ChatDataManager数据
            val chatDataManager = com.example.aifloatingball.data.ChatDataManager.getInstance(this)
            chatDataManager.forceReloadAllData()
            
            // 刷新UI
            runOnUiThread {
                try {
                    // 刷新联系人适配器
                    chatContactAdapter?.notifyDataSetChanged()
                    
                    // 如果当前显示的是对话tab，强制刷新联系人列表
                    val currentTab = findViewById<com.google.android.material.tabs.TabLayout>(R.id.chat_tab_layout)?.selectedTabPosition
                    if (currentTab == 0) { // 对话tab
                        // 重新生成联系人数据
                        refreshContactListData()
                    }
                    
                    // 发送广播通知所有ChatActivity更新
                    val intent = Intent("com.example.aifloatingball.AI_CHAT_UPDATED")
                    intent.putExtra("action", "refresh_all")
                    sendBroadcast(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "刷新UI失败", e)
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "刷新AI对话数据失败", e)
        }
    }
    
    /**
     * 刷新联系人列表数据
     */
    private fun refreshContactListData() {
        try {
            // 重新加载和更新联系人数据
            val aiNames = listOf("DeepSeek", "Kimi", "智谱AI", "ChatGPT", "Claude", "Gemini")
            val chatDataManager = com.example.aifloatingball.data.ChatDataManager.getInstance(this)
            
            aiNames.forEach { aiName ->
                val serviceType = getAIServiceTypeFromName(aiName)
                if (serviceType != null) {
                    val processedName = if (aiName.contains(Regex("[\\u4e00-\\u9fff]"))) {
                        aiName
                    } else {
                        aiName.lowercase()
                    }
                    val contactId = "ai_${processedName.replace(" ", "_")}"
                    
                    // 使用与getLastChatMessageFromHistory相同的逻辑：获取所有会话中的最新消息
                    val allSessions = chatDataManager.getAllSessions(serviceType)
                    val latestSession = allSessions.maxByOrNull { it.updatedAt }
                    
                    if (latestSession != null && latestSession.messages.isNotEmpty()) {
                        val lastMessage = latestSession.messages.last()
                        // 更新对应联系人的最后消息
                        updateContactLastMessageData(aiName, contactId, lastMessage)
                        Log.d(TAG, "刷新联系人列表 - $aiName 最新消息: ${lastMessage.content.take(30)}...")
                    } else {
                        Log.d(TAG, "刷新联系人列表 - $aiName 暂无历史消息")
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "刷新联系人列表数据失败", e)
        }
    }
    
    /**
     * 更新联系人的最后消息数据
     */
    private fun updateContactLastMessageData(aiName: String, contactId: String, lastMessage: com.example.aifloatingball.data.ChatDataManager.ChatMessage) {
        try {
            // 在allContacts中找到对应的联系人并更新
            for (i in allContacts.indices) {
                val category = allContacts[i]
                val contactIndex = category.contacts.indexOfFirst { it.id == contactId }
                if (contactIndex != -1) {
                    val mutableContacts = category.contacts.toMutableList()
                    val updatedContact = mutableContacts[contactIndex].copy(
                        lastMessage = lastMessage.content.take(50) + if (lastMessage.content.length > 50) "..." else "",
                        lastMessageTime = lastMessage.timestamp
                    )
                    mutableContacts[contactIndex] = updatedContact
                    allContacts[i] = category.copy(contacts = mutableContacts)
                    
                    Log.d(TAG, "已更新联系人 $aiName 的最后消息: ${lastMessage.content.take(30)}...")
                    break
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "更新联系人最后消息数据失败", e)
        }
    }
    
    /**
     * 文件同步监控
     */
    private var fileSyncHandler: Handler? = null
    private var fileSyncRunnable: Runnable? = null
    private val fileSyncInterval = 5000L // 5秒检查一次
    
    /**
     * 启动文件监听同步机制
     */
    private fun startFileSyncMonitoring() {
        try {
            Log.d(TAG, "启动文件监听同步机制")
            
            fileSyncHandler = Handler(Looper.getMainLooper())
            fileSyncRunnable = object : Runnable {
                override fun run() {
                    checkSyncFiles()
                    fileSyncHandler?.postDelayed(this, fileSyncInterval)
                }
            }
            
            fileSyncHandler?.post(fileSyncRunnable!!)
            Log.d(TAG, "文件监听同步机制已启动")
            
        } catch (e: Exception) {
            Log.e(TAG, "启动文件监听同步机制失败", e)
        }
    }
    
    /**
     * 检查同步文件
     */
    private fun checkSyncFiles() {
        try {
            val aiServices = listOf("deepseek", "kimi", "zhipu_ai", "chatgpt", "claude", "gemini")
            val filesDir = filesDir
            
            aiServices.forEach { serviceName ->
                val syncFile = File(filesDir, "ai_sync_$serviceName.json")
                if (syncFile.exists()) {
                    val lastModified = syncFile.lastModified()
                    val currentTime = System.currentTimeMillis()
                    
                    // 如果文件在最近10秒内被修改，说明有新的同步数据
                    if (currentTime - lastModified < 10000L) {
                        Log.d(TAG, "检测到同步文件更新: $serviceName")
                        processSyncFile(syncFile, serviceName)
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "检查同步文件失败", e)
        }
    }
    
    /**
     * 处理同步文件
     */
    private fun processSyncFile(syncFile: File, serviceName: String) {
        try {
            val jsonString = syncFile.readText()
            val syncData = JSONObject(jsonString)
            
            val serviceTypeName = syncData.getString("service_type")
            val sessionId = syncData.getString("session_id")
            val messageCount = syncData.getInt("message_count")
            val timestamp = syncData.getLong("timestamp")
            
            Log.d(TAG, "处理同步文件:")
            Log.d(TAG, "  服务类型: $serviceTypeName")
            Log.d(TAG, "  会话ID: $sessionId")
            Log.d(TAG, "  消息数: $messageCount")
            Log.d(TAG, "  时间戳: $timestamp")
            
            // 强制刷新数据
            refreshAIContactData()
            
            // 删除已处理的同步文件
            syncFile.delete()
            Log.d(TAG, "同步文件已处理并删除")
            
        } catch (e: Exception) {
            Log.e(TAG, "处理同步文件失败", e)
        }
    }
    
    /**
     * 停止文件监听同步机制
     */
    private fun stopFileSyncMonitoring() {
        try {
            fileSyncRunnable?.let { runnable ->
                fileSyncHandler?.removeCallbacks(runnable)
            }
            fileSyncHandler = null
            fileSyncRunnable = null
            Log.d(TAG, "文件监听同步机制已停止")
        } catch (e: Exception) {
            Log.e(TAG, "停止文件监听同步机制失败", e)
        }
    }
    
    /**
     * 定时强制同步机制
     */
    private var periodicSyncHandler: Handler? = null
    private var periodicSyncRunnable: Runnable? = null
    private val periodicSyncInterval = 10000L // 10秒同步一次
    
    /**
     * 启动定时强制同步
     */
    private fun startPeriodicSync() {
        try {
            Log.d(TAG, "启动定时强制同步机制")
            
            periodicSyncHandler = Handler(Looper.getMainLooper())
            periodicSyncRunnable = object : Runnable {
                override fun run() {
                    Log.d(TAG, "执行定时强制同步...")
                    forceRefreshAIData()
                    periodicSyncHandler?.postDelayed(this, periodicSyncInterval)
                }
            }
            
            periodicSyncHandler?.post(periodicSyncRunnable!!)
            Log.d(TAG, "定时强制同步机制已启动")
            
        } catch (e: Exception) {
            Log.e(TAG, "启动定时强制同步机制失败", e)
        }
    }
    
    /**
     * 广播接收器，用于接收AI消息更新通知
     */
    private val aiMessageUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            try {
                Log.d(TAG, "收到广播: action=${intent?.action}")
                if (intent?.action == "com.example.aifloatingball.AI_MESSAGE_UPDATED") {
                    val contactId = intent.getStringExtra("contact_id") ?: return
                    val contactName = intent.getStringExtra("contact_name") ?: return
                    val lastMessage = intent.getStringExtra("last_message") ?: return
                    val lastMessageTime = intent.getLongExtra("last_message_time", System.currentTimeMillis())
                    
                    Log.d(TAG, "收到AI消息更新广播: $contactName ($contactId) - ${lastMessage.take(50)}...")
                    
                    // 更新联系人数据
                    updateContactFromBroadcast(contactId, contactName, lastMessage, lastMessageTime)
                }
            } catch (e: Exception) {
                Log.e(TAG, "处理AI消息更新广播失败", e)
            }
        }
    }
    
    /**
     * 从广播更新联系人数据
     */
    private fun updateContactFromBroadcast(contactId: String, contactName: String, lastMessage: String, lastMessageTime: Long) {
        try {
            Log.d(TAG, "开始更新联系人数据: $contactName ($contactId) - ${lastMessage.take(50)}...")
            
            // 打印所有可用的联系人ID用于调试
            Log.d(TAG, "当前allContacts中的联系人ID:")
            allContacts.forEach { category ->
                category.contacts.forEach { contact ->
                    Log.d(TAG, "  - ${contact.name} (${contact.id})")
                }
            }
            
            // 在allContacts中找到对应的联系人并更新
            var found = false
            for (i in allContacts.indices) {
                val category = allContacts[i]
                val contactIndex = category.contacts.indexOfFirst { it.id == contactId }
                if (contactIndex != -1) {
                    Log.d(TAG, "找到匹配的联系人: ${category.contacts[contactIndex].name} (${category.contacts[contactIndex].id}) -> $contactName ($contactId)")
                    found = true
                    
                    val mutableContacts = category.contacts.toMutableList()
                    val updatedContact = mutableContacts[contactIndex].copy(
                        lastMessage = lastMessage.take(50) + if (lastMessage.length > 50) "..." else "",
                        lastMessageTime = lastMessageTime
                    )
                    mutableContacts[contactIndex] = updatedContact
                    allContacts[i] = category.copy(contacts = mutableContacts)
                    
                    // 通知适配器更新
                    chatContactAdapter?.notifyDataSetChanged()
                    
                    Log.d(TAG, "已从广播更新联系人 $contactName 的最后消息: ${lastMessage.take(30)}...")
                    break
                }
            }
            
            if (!found) {
                Log.w(TAG, "未找到匹配的联系人: $contactName ($contactId)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "从广播更新联系人数据失败", e)
        }
    }
    
    /**
     * 注册广播接收器
     */
    private fun registerBroadcastReceiver() {
        try {
            val filter = IntentFilter("com.example.aifloatingball.AI_MESSAGE_UPDATED")
            registerReceiver(aiMessageUpdateReceiver, filter)
            Log.d(TAG, "已注册AI消息更新广播接收器")
        } catch (e: Exception) {
            Log.e(TAG, "注册广播接收器失败", e)
        }
    }
    
    /**
     * 注销广播接收器
     */
    private fun unregisterBroadcastReceiver() {
        try {
            unregisterReceiver(aiMessageUpdateReceiver)
            Log.d(TAG, "已注销AI消息更新广播接收器")
        } catch (e: Exception) {
            Log.e(TAG, "注销广播接收器失败", e)
        }
    }
    
    /**
     * 强制加载AI联系人列表数据汇总
     */
    private fun forceLoadContactDataSummary() {
        try {
            Log.d(TAG, "开始强制加载AI联系人列表数据汇总")
            
            val chatDataManager = com.example.aifloatingball.data.ChatDataManager.getInstance(this)
            val aiNames = listOf("DeepSeek", "Kimi", "智谱AI", "ChatGPT", "Claude", "Gemini")
            
            aiNames.forEach { aiName ->
                val serviceType = getAIServiceTypeFromName(aiName)
                if (serviceType != null) {
                    val processedName = if (aiName.contains(Regex("[\\u4e00-\\u9fff]"))) {
                        aiName
                    } else {
                        aiName.lowercase()
                    }
                    val contactId = "ai_${processedName.replace(" ", "_")}"
                    
                    // 获取所有会话，找到最新的有消息的会话
                    val allSessions = chatDataManager.getAllSessions(serviceType)
                    val latestSession = allSessions.maxByOrNull { it.updatedAt }
                    
                    if (latestSession != null && latestSession.messages.isNotEmpty()) {
                        val lastMessage = latestSession.messages.last()
                        
                        // 更新联系人数据
                        updateContactFromBroadcast(contactId, aiName, lastMessage.content, lastMessage.timestamp)
                        
                        Log.d(TAG, "强制加载 - $aiName 最新消息: ${lastMessage.content.take(30)}...")
                    } else {
                        Log.d(TAG, "强制加载 - $aiName 暂无历史消息")
                    }
                }
            }
            
            Log.d(TAG, "强制加载AI联系人列表数据汇总完成")
        } catch (e: Exception) {
            Log.e(TAG, "强制加载AI联系人列表数据汇总失败", e)
        }
    }
    
    /**
     * 停止定时强制同步
     */
    private fun stopPeriodicSync() {
        try {
            periodicSyncRunnable?.let { runnable ->
                periodicSyncHandler?.removeCallbacks(runnable)
            }
            periodicSyncHandler = null
            periodicSyncRunnable = null
            Log.d(TAG, "定时强制同步机制已停止")
        } catch (e: Exception) {
            Log.e(TAG, "停止定时强制同步机制失败", e)
        }
    }
    
    /**
     * 添加测试刷新按钮
     */
    private fun addTestRefreshButton() {
        try {
            // 在对话tab中添加一个测试按钮
            val chatTabLayout = findViewById<com.google.android.material.tabs.TabLayout>(R.id.chat_tab_layout)
            chatTabLayout?.let { tabLayout ->
                // 添加一个测试标签页
                val testTab = tabLayout.newTab().setText("测试")
                tabLayout.addTab(testTab)
                
                // 设置点击监听器
                tabLayout.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
                    override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                        if (tab?.text == "测试") {
                            Log.d(TAG, "测试标签页被选中 - 开始调试数据")
                            debugAllAIData()
                            forceRefreshAIData()
                            
                            // 显示调试信息
                            Toast.makeText(this@SimpleModeActivity, "数据调试完成，请查看日志", Toast.LENGTH_LONG).show()
                            
                            // 切换回对话标签页
                            tabLayout.getTabAt(0)?.select()
                        }
                    }
                    
                    override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
                    override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
                })
            }
        } catch (e: Exception) {
            Log.e(TAG, "添加测试刷新按钮失败", e)
        }
    }
    
    /**
     * 更新联系人的最后消息
     */
    private fun updateContactLastMessage(aiContact: ChatContact, lastMessage: String) {
        try {
            var updated = false
            // 找到并更新联系人
            for (i in allContacts.indices) {
                val category = allContacts[i]
                val contactIndex = category.contacts.indexOfFirst { it.id == aiContact.id }
                if (contactIndex != -1) {
                    val mutableContacts = category.contacts.toMutableList()
                    mutableContacts[contactIndex] = mutableContacts[contactIndex].copy(
                        lastMessage = lastMessage.take(50) + if (lastMessage.length > 50) "..." else "",
                        lastMessageTime = System.currentTimeMillis()
                    )
                    allContacts[i] = category.copy(contacts = mutableContacts)
                    updated = true
                    break
                }
            }

            // 只有在成功更新联系人数据时才刷新UI，避免不必要的重新加载
            if (updated) {
                // 使用更轻量的方式通知适配器数据变化，而不是完全重新加载
                chatContactAdapter?.notifyDataSetChanged()
                Log.d(TAG, "已更新联系人 ${aiContact.name} 的最后消息")
            }

        } catch (e: Exception) {
            Log.e(TAG, "更新联系人最后消息失败", e)
        }
    }



    /**
     * 检查联系人是否有有效的API密钥
     */
    private fun hasValidApiKey(contact: ChatContact): Boolean {
        return try {
            // 获取AI服务类型
            val serviceType = getAIServiceType(contact)
            if (serviceType != null) {
                // 从SettingsManager获取API密钥
                val apiKey = getApiKeyForService(serviceType)
                apiKey.isNotBlank() && apiKey.length > 10
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "检查API密钥失败: ${contact.name}", e)
            false
        }
    }

    /**
     * 打开与联系人的对话并发送消息
     */
    private fun openChatWithContactAndMessage(contact: ChatContact, message: String) {
        try {
            val intent = Intent(this, ChatActivity::class.java).apply {
                putExtra(ChatActivity.EXTRA_CONTACT, contact)
                putExtra("auto_send_message", message) // 传递要自动发送的消息
            }
            startActivity(intent)
            Log.d(TAG, "打开对话并发送消息: ${contact.name} - $message")
        } catch (e: Exception) {
            Log.e(TAG, "打开对话失败", e)
            Toast.makeText(this, "打开对话失败", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 显示AI联系人
     */
    private fun showAIContacts() {
        try {
            // 使用AI标签管理器获取AI助手标签下的AI对象
            val aiTagManager = AITagManager.getInstance(this)
            val aiContacts = aiTagManager.getAIsByTag("ai_assistant", allContacts)

            Log.d(TAG, "showAIContacts: 开始显示AI联系人")
            Log.d(TAG, "showAIContacts: 总分类数: ${allContacts.size}")
            allContacts.forEach { category ->
                Log.d(TAG, "showAIContacts: 分类: ${category.name}, 联系人数: ${category.contacts.size}")
                category.contacts.forEach { contact ->
                    Log.d(TAG, "showAIContacts: - ${contact.name} (类型: ${contact.type})")
                }
            }

            // 将List<ChatContact>包装成List<ContactCategory>
            val aiContactCategory = listOf(ContactCategory(
                name = "AI助手",
                contacts = aiContacts.toMutableList(),
                isExpanded = true
            ))

            chatContactAdapter?.updateContacts(aiContactCategory)
            Log.d(TAG, "显示AI联系人，AI助手标签下AI数量: ${aiContacts.size}")
            aiContacts.forEach { ai ->
                Log.d(TAG, "showAIContacts: AI助手标签下的AI: ${ai.name}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "显示AI联系人失败", e)
        }
    }

    /**
     * 判断是否为AI联系人
     */
    private fun isAIContact(contact: ChatContact): Boolean {
        return when (contact.name.lowercase()) {
            "chatgpt", "gpt", "claude", "gemini", "文心一言", "wenxin",
            "deepseek", "通义千问", "qianwen", "讯飞星火", "xinghuo",
            "kimi", "智谱ai", "zhipuai" -> true
            else -> false
        }
    }



    /**
     * 打开AI联系人列表界面
     */
    private fun openAIContactListActivity() {
        try {
            val intent = Intent(this, AIContactListActivity::class.java)
            startActivityForResult(intent, REQUEST_CODE_ADD_AI_CONTACT)
            Log.d(TAG, "打开AI联系人列表界面")
        } catch (e: Exception) {
            Log.e(TAG, "打开AI联系人列表界面失败", e)
            Toast.makeText(this, "打开AI联系人列表界面失败", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 显示所有用户添加的AI助手（"全部"标签）
     * 这是一个特殊的聚合视图，显示所有分组中的AI，不显示分组标题
     */
    private fun showAllUserAIContacts() {
        try {
            // 收集所有AI联系人
            val aiContacts = mutableListOf<ChatContact>()
            this.allContacts.forEach { category ->
                if (category.name != "全部") {
                    val validAIContacts = category.contacts.filter { contact ->
                        contact.type == ContactType.AI &&
                        !contact.id.contains("hint") &&
                        !contact.id.contains("empty") &&
                        contact.name != "暂无AI助手" &&
                        contact.name != "AI助手分组为空"
                    }
                    aiContacts.addAll(validAIContacts)
                }
            }
            
            // 从UnifiedGroupChatManager获取群聊数据
            val groupChatContacts = mutableListOf<ChatContact>()
            try {
                val groupChats = unifiedGroupChatManager.getAllGroupChats()
                groupChats.forEach { groupChat ->
                    val groupChatContact = ChatContact(
                        id = groupChat.id,
                        name = groupChat.name,
                        avatar = groupChat.avatar,
                        type = ContactType.GROUP,
                        description = groupChat.description,
                        isOnline = true,
                        lastMessage = groupChat.lastMessage,
                        lastMessageTime = groupChat.lastMessageTime,
                        unreadCount = groupChat.unreadCount,
                        isPinned = groupChat.isPinned,
                        isMuted = groupChat.isMuted,
                        groupId = groupChat.id,
                        memberCount = groupChat.members.size,
                        aiMembers = groupChat.members.filter { member -> member.type == MemberType.AI }.map { member -> member.name }
                    )
                    groupChatContacts.add(groupChatContact)
                }
            } catch (e: Exception) {
                Log.e(TAG, "从统一管理器获取群聊数据失败", e)
            }
            
            // 合并AI和群聊联系人
            val allContacts = mutableListOf<ChatContact>()
            allContacts.addAll(aiContacts)
            allContacts.addAll(groupChatContacts)
            
            Log.d(TAG, "收集到 ${allContacts.size} 个联系人（${aiContacts.size} 个AI，${groupChatContacts.size} 个群聊）")

            // 按置顶状态和最后消息时间排序
            val sortedContacts = allContacts.distinctBy { it.id }.sortedWith(
                compareByDescending<ChatContact> { it.isPinned }
                    .thenByDescending { it.lastMessageTime }
            )

            Log.d(TAG, "全部标签页显示 ${sortedContacts.size} 个联系人（AI和群聊）")

            val displayCategories = listOf(ContactCategory(
                name = "全部",
                contacts = sortedContacts.toMutableList(),
                isExpanded = true,
                isPinned = false
            ))

            // 确保所有显示的AI都在allContacts中（用于长按菜单功能）
            ensureAIsInAllContacts(displayCategories)

            // 同步AI联系人状态（确保显示的AI与存储的AI状态一致）
            syncAIContactStates(displayCategories)

            chatContactAdapter?.updateContacts(displayCategories)
            Log.d(TAG, "显示全部用户联系人，共${sortedContacts.size}个联系人（AI和群聊）")
        } catch (e: Exception) {
            Log.e(TAG, "显示全部用户AI助手失败", e)
        }
    }

    /**
     * 显示AI助手预设分组（只显示用户主动移动到此分组的AI）
     */
    private fun showAIAssistantGroup() {
        try {
            // 查找"AI助手"分组
            val aiAssistantCategory = allContacts.find { it.name == "AI助手" }

            // 过滤出真正的AI联系人（排除提示性的联系人）
            val realAIContacts = aiAssistantCategory?.contacts?.filter { contact ->
                contact.type == ContactType.AI &&
                !contact.id.contains("hint") &&
                !contact.id.contains("empty") &&
                contact.name != "暂无AI助手"
            } ?: emptyList()

            val displayCategories = if (realAIContacts.isNotEmpty()) {
                // 只显示真正的AI联系人
                listOf(ContactCategory(
                    name = "AI助手",
                    contacts = realAIContacts.toMutableList(),
                    isExpanded = true
                ))
            } else {
                // 如果AI助手分组为空，显示空分组（不添加虚假联系人）
                listOf(ContactCategory(
                    name = "AI助手",
                    contacts = mutableListOf(),
                    isExpanded = true
                ))
            }

            chatContactAdapter?.updateContacts(displayCategories)
            Log.d(TAG, "显示AI助手预设分组，真实AI数量: ${realAIContacts.size}")
        } catch (e: Exception) {
            Log.e(TAG, "显示AI助手预设分组失败", e)
        }
    }

    /**
     * 执行标签内容切换（不使用动画）
     * 基于标签名称而不是位置进行切换
     */
    private fun executeTabContentSwitch(position: Int, tab: com.google.android.material.tabs.TabLayout.Tab?) {
        try {
            val tabText = tab?.text?.toString() ?: return

            when (tabText) {
                "全部" -> {
                    // "全部"标签 - 显示所有分组中的AI助手聚合视图
                    showAllUserAIContacts()
                    Log.d(TAG, "切换到全部标签页")
                }
                "AI助手" -> {
                    // "AI助手"标签 - 显示预设分组中的AI
                    showAIAssistantGroup()
                    Log.d(TAG, "切换到AI助手标签页")
                }
                "+" -> {
                    // +号按钮 - 只有在用户主动点击时才创建新分组
                    // 如果是拖拽导致的切换，不执行任何操作
                    if (!isDraggingTabs) {
                        showCreateCustomGroupDialog()
                        // 选择回到上一个有效标签页
                        val chatTabLayout = findViewById<com.google.android.material.tabs.TabLayout>(R.id.chat_tab_layout)
                        val previousTab = if (currentTabPosition > 0) currentTabPosition - 1 else 0
                        chatTabLayout?.getTabAt(previousTab)?.select()
                    } else {
                        Log.d(TAG, "拖拽操作中，跳过+号按钮处理")
                    }
                    return
                }
                else -> {
                    // 自定义分组标签页
                    showCustomGroupContacts(tabText)
                    Log.d(TAG, "切换到自定义分组标签页: $tabText")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "执行标签内容切换失败", e)
        }
    }

    /**
     * 显示创建自定义分组对话框
     */
    private fun showCreateCustomGroupDialog() {
        try {
            val input = android.widget.EditText(this).apply {
                hint = "输入分组名称"
                setPadding(50, 30, 50, 30)
            }

            AlertDialog.Builder(this, R.style.Theme_MaterialDialog)
                .setTitle("创建新分组")
                .setMessage("为AI助手创建新的分组")
                .setView(input)
                .setPositiveButton("创建") { _, _ ->
                    val groupName = input.text.toString().trim()
                    if (groupName.isNotEmpty()) {
                        createCustomGroup(groupName)
                        Toast.makeText(this, "已创建分组: $groupName", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "分组名称不能为空", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("取消", null)
                .show()
        } catch (e: Exception) {
            Log.e(TAG, "显示创建自定义分组对话框失败", e)
            Toast.makeText(this, "创建分组失败", Toast.LENGTH_SHORT).show()
        }
    }



    /**
     * AI选择项数据类
     */
    private data class AISelectionItem(
        val name: String,
        val description: String,
        val iconRes: Int,
        val colorHex: String
    )

    /**
     * 显示简化的AI选择对话框
     */
    private fun showMaterialAISelectionDialog(aiList: List<AISelectionItem>) {
        try {
            // 创建对话框布局
            val dialogView = layoutInflater.inflate(R.layout.dialog_ai_add_simple, null)
            val container = dialogView.findViewById<LinearLayout>(R.id.ai_selection_container)
            val btnCancel = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_cancel)
            val btnConfirm = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_confirm)

            // 存储选中的AI
            val selectedAIs = mutableListOf<AISelectionItem>()

            // 动态创建AI选择项
            aiList.forEach { aiItem ->
                val itemView = layoutInflater.inflate(R.layout.item_ai_selection_simple, container, false)

                val aiIcon = itemView.findViewById<ImageView>(R.id.ai_icon)
                val aiName = itemView.findViewById<TextView>(R.id.ai_name)
                val aiSwitch = itemView.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.ai_switch)
                val apiKeyInputLayout = itemView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.api_key_input_layout)
                val apiKeyInput = itemView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.api_key_input)
                val statusContainer = itemView.findViewById<LinearLayout>(R.id.status_container)
                val statusIcon = itemView.findViewById<ImageView>(R.id.status_icon)
                val statusText = itemView.findViewById<TextView>(R.id.status_text)

                // 设置AI信息
                aiName.text = aiItem.name

                // 使用FaviconLoader加载AI图标
                FaviconLoader.loadAIEngineIcon(aiIcon, aiItem.name, R.drawable.ic_smart_toy)

                // 检查API密钥和联系人状态
                val hasApiKey = getApiKeyForAI(aiItem.name).isNotBlank()
                val hasContact = hasAIContact(aiItem.name)

                if (hasContact && hasApiKey) {
                    // 已添加状态
                    statusContainer.visibility = android.view.View.VISIBLE
                    statusText.text = "已添加"
                    statusIcon.setImageResource(R.drawable.ic_check_circle)
                    aiSwitch.isEnabled = false
                    itemView.alpha = 0.6f
                } else {
                    // 设置API密钥
                    if (hasApiKey) {
                        apiKeyInput.setText(getApiKeyForAI(aiItem.name))
                        aiSwitch.isChecked = true
                    }

                    // 开关状态变化监听
                    aiSwitch.setOnCheckedChangeListener { _, isChecked ->
                        if (isChecked) {
                            apiKeyInputLayout.visibility = android.view.View.VISIBLE
                            if (!selectedAIs.contains(aiItem)) {
                                selectedAIs.add(aiItem)
                            }
                        } else {
                            apiKeyInputLayout.visibility = android.view.View.GONE
                            selectedAIs.remove(aiItem)
                        }
                        btnConfirm.isEnabled = selectedAIs.isNotEmpty()
                    }

                    // 如果已选中，显示API密钥输入框
                    if (aiSwitch.isChecked) {
                        apiKeyInputLayout.visibility = android.view.View.VISIBLE
                        selectedAIs.add(aiItem)
                    }
                }

                container.addView(itemView)
            }

            // 创建对话框
            val dialog = androidx.appcompat.app.AlertDialog.Builder(this, R.style.Theme_MaterialDialog)
                .setView(dialogView)
                .setCancelable(true)
                .create()

            // 设置按钮事件
            btnCancel.setOnClickListener { dialog.dismiss() }

            btnConfirm.setOnClickListener {
                // 处理选中的AI
                selectedAIs.forEach { aiItem ->
                    // 查找对应的输入框并保存API密钥
                    for (i in 0 until container.childCount) {
                        val childView = container.getChildAt(i)
                        val nameView = childView.findViewById<TextView>(R.id.ai_name)
                        if (nameView.text.toString() == aiItem.name) {
                            val apiKeyInput = childView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.api_key_input)
                            val apiKey = apiKeyInput.text.toString().trim()

                            if (apiKey.isNotEmpty()) {
                                // 保存API密钥
                                saveApiKeyForAI(aiItem.name, apiKey)
                            }

                            // 添加AI联系人
                            addAIContactToCategory(aiItem.name, "AI助手", "")
                            break
                        }
                    }
                }

                chatContactAdapter?.updateContacts(allContacts)
                dialog.dismiss()

                if (selectedAIs.isNotEmpty()) {
                    Toast.makeText(this, "已添加 ${selectedAIs.size} 个AI助手", Toast.LENGTH_SHORT).show()
                }
            }

            // 初始状态
            btnConfirm.isEnabled = false

            dialog.show()

        } catch (e: Exception) {
            Log.e(TAG, "显示Material AI选择对话框失败", e)
        }
    }

    /**
     * 显示统一的AI选择对话框
     */
    private fun showUnifiedAISelectionDialog() {
        try {
            // 创建主对话框
            val dialogBuilder = AlertDialog.Builder(this)
            dialogBuilder.setTitle("📱 添加AI助手")
            dialogBuilder.setMessage("选择您要添加的AI助手类型：")

            // 定义AI选项
            val aiOptions = arrayOf(
                "🔍 搜索AI助手 - 从预设列表中选择",
                "⚙️ 自定义API - 填写自定义API信息"
            )

            dialogBuilder.setItems(aiOptions) { _, which ->
                when (which) {
                    0 -> showPresetAISelectionDialog() // 显示预设AI列表
                    1 -> showCustomAPIDialog() // 显示自定义API对话框
                }
            }

            dialogBuilder.setNegativeButton("取消", null)
            dialogBuilder.show()

        } catch (e: Exception) {
            Log.e(TAG, "显示统一AI选择对话框失败", e)
        }
    }

    /**
     * 显示预设AI选择对话框（支持勾选）
     */
    private fun showPresetAISelectionDialog() {
        try {
            // 定义预设AI列表
            val presetAIs = listOf(
                "DeepSeek" to "🚀 DeepSeek - 性能强劲，支持中文",
                "智谱AI" to "🧠 智谱AI - GLM-4大语言模型",
                "ChatGPT" to "🤖 ChatGPT - OpenAI的经典模型",
                "Claude" to "💡 Claude - Anthropic的智能助手",
                "Gemini" to "🌟 Gemini - Google的AI助手",
                "Kimi" to "🌙 Kimi - Moonshot的长文本专家",
                "文心一言" to "📚 文心一言 - 百度的大语言模型",
                "通义千问" to "🎯 通义千问 - 阿里巴巴的AI",
                "讯飞星火" to "⚡ 讯飞星火 - 科大讯飞的AI"
            )

            // 创建自定义布局
            val dialogLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(40, 30, 40, 30)
            }

            // 标题
            val titleText = TextView(this).apply {
                text = "📱 添加AI助手"
                textSize = 18f
                setTextColor(getColor(R.color.simple_mode_text_primary_light))
                setPadding(0, 0, 0, 20)
                gravity = android.view.Gravity.CENTER
            }
            dialogLayout.addView(titleText)

            // 说明文字
            val instructionText = TextView(this).apply {
                text = "勾选要添加的AI助手，然后配置API密钥："
                textSize = 14f
                setTextColor(getColor(R.color.simple_mode_text_secondary_light))
                setPadding(0, 0, 0, 15)
            }
            dialogLayout.addView(instructionText)

            // AI选择列表
            val aiCheckBoxes = mutableListOf<CheckBox>()
            presetAIs.forEach { (aiName, description) ->
                val hasApiKey = getApiKeyForAI(aiName).isNotBlank()
                val hasContact = hasAIContact(aiName)

                val checkBox = CheckBox(this).apply {
                    text = when {
                        hasContact && hasApiKey -> "$description ✅"
                        hasApiKey -> "$description ⚠️ 有密钥"
                        else -> description
                    }
                    textSize = 14f
                    setPadding(0, 8, 0, 8)
                    isChecked = !hasContact && hasApiKey // 有密钥但没联系人的默认勾选
                }

                dialogLayout.addView(checkBox)
                aiCheckBoxes.add(checkBox)
            }

            // 创建对话框
            val dialog = AlertDialog.Builder(this)
                .setView(dialogLayout)
                .setPositiveButton("配置选中的AI") { _, _ ->
                    val selectedAIs = mutableListOf<Pair<String, String>>()
                    aiCheckBoxes.forEachIndexed { index, checkBox ->
                        if (checkBox.isChecked) {
                            selectedAIs.add(presetAIs[index])
                        }
                    }

                    if (selectedAIs.isNotEmpty()) {
                        showBatchConfigurationDialog(selectedAIs)
                    } else {
                        Toast.makeText(this@SimpleModeActivity, "请至少选择一个AI助手", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("取消", null)
                .create()

            dialog.show()

        } catch (e: Exception) {
            Log.e(TAG, "显示预设AI选择对话框失败", e)
        }
    }

    /**
     * 显示批量配置对话框
     */
    private fun showBatchConfigurationDialog(selectedAIs: List<Pair<String, String>>) {
        try {
            // 创建批量配置界面
            val dialogLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(40, 30, 40, 30)
            }

            // 标题
            val titleText = TextView(this).apply {
                text = "🔧 批量配置AI助手"
                textSize = 18f
                setTextColor(getColor(R.color.simple_mode_text_primary_light))
                setPadding(0, 0, 0, 20)
                gravity = android.view.Gravity.CENTER
            }
            dialogLayout.addView(titleText)

            // 说明文字
            val instructionText = TextView(this).apply {
                text = "为选中的AI助手配置API密钥："
                textSize = 14f
                setTextColor(getColor(R.color.simple_mode_text_secondary_light))
                setPadding(0, 0, 0, 15)
            }
            dialogLayout.addView(instructionText)

            // 滚动容器
            val scrollView = ScrollView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    400 // 限制高度
                )
            }

            val scrollContent = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 0, 0, 0)
            }

            // 为每个AI创建配置项
            val apiKeyInputs = mutableMapOf<String, EditText>()
            selectedAIs.forEach { (aiName, description) ->
                // AI名称
                val aiNameText = TextView(this).apply {
                    text = description
                    textSize = 14f
                    setTextColor(getColor(R.color.simple_mode_text_primary_light))
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setPadding(0, 15, 0, 8)
                }
                scrollContent.addView(aiNameText)

                // API密钥输入框
                val apiKeyInput = EditText(this).apply {
                    hint = "输入${aiName}的API密钥"
                    inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                    setPadding(20, 15, 20, 15)
                    background = getDrawable(R.drawable.edit_text_background)

                    // 如果已有API密钥，预填充
                    val existingApiKey = getApiKeyForAI(aiName)
                    if (existingApiKey.isNotBlank()) {
                        setText(existingApiKey)
                    }
                }
                scrollContent.addView(apiKeyInput)
                apiKeyInputs[aiName] = apiKeyInput

                // 间距
                val spacer = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        10
                    )
                }
                scrollContent.addView(spacer)
            }

            scrollView.addView(scrollContent)
            dialogLayout.addView(scrollView)

            // 创建对话框
            val dialog = AlertDialog.Builder(this)
                .setView(dialogLayout)
                .setPositiveButton("保存并添加") { _, _ ->
                    var successCount = 0
                    var errorCount = 0

                    selectedAIs.forEach { (aiName, description) ->
                        try {
                            val apiKey = apiKeyInputs[aiName]?.text?.toString()?.trim() ?: ""
                            if (apiKey.isNotBlank() && isValidApiKey(apiKey, aiName)) {
                                // 保存API密钥
                                saveApiKeyForAI(aiName, apiKey)

                                // 如果还没有联系人，则添加
                                if (!hasAIContact(aiName)) {
                                    addAIContactToCategory(aiName, description, apiKey)
                                }

                                successCount++
                            } else {
                                errorCount++
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "配置${aiName}失败", e)
                            errorCount++
                        }
                    }

                    // 显示结果
                    val message = when {
                        successCount > 0 && errorCount == 0 -> "成功配置${successCount}个AI助手"
                        successCount > 0 && errorCount > 0 -> "成功配置${successCount}个，失败${errorCount}个AI助手"
                        else -> "配置失败，请检查API密钥格式"
                    }
                    Toast.makeText(this@SimpleModeActivity, message, Toast.LENGTH_LONG).show()

                    // 刷新UI
                    if (successCount > 0) {
                        chatContactAdapter?.updateContacts(allContacts)
                    }
                }
                .setNeutralButton("测试连通性") { _, _ ->
                    // 批量测试API连通性
                    batchTestApiConnections(selectedAIs, apiKeyInputs)
                }
                .setNegativeButton("取消", null)
                .create()

            dialog.show()

        } catch (e: Exception) {
            Log.e(TAG, "显示批量配置对话框失败", e)
        }
    }

    /**
     * 批量测试API连通性
     */
    private fun batchTestApiConnections(
        selectedAIs: List<Pair<String, String>>,
        apiKeyInputs: Map<String, EditText>
    ) {
        try {
            val testResults = mutableMapOf<String, String>()
            var completedTests = 0
            val totalTests = selectedAIs.size

            // 显示测试进度对话框
            val progressDialog = AlertDialog.Builder(this)
                .setTitle("🧪 测试连通性")
                .setMessage("正在测试API连接...")
                .setCancelable(false)
                .create()
            progressDialog.show()

            selectedAIs.forEach { (aiName, _) ->
                val apiKey = apiKeyInputs[aiName]?.text?.toString()?.trim() ?: ""

                if (apiKey.isNotBlank()) {
                    lifecycleScope.launch {
                        try {
                            val result = performAPITest(aiName, apiKey)
                            testResults[aiName] = if (result.success) "✅ ${result.message}" else "❌ ${result.message}"
                        } catch (e: Exception) {
                            testResults[aiName] = "❌ 测试失败: ${e.message}"
                        }

                        completedTests++
                        if (completedTests >= totalTests) {
                            runOnUiThread {
                                progressDialog.dismiss()
                                showTestResults(testResults)
                            }
                        }
                    }
                } else {
                    testResults[aiName] = "⚠️ API密钥为空"
                    completedTests++
                    if (completedTests >= totalTests) {
                        progressDialog.dismiss()
                        showTestResults(testResults)
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "批量测试API连通性失败", e)
        }
    }

    /**
     * 显示测试结果
     */
    private fun showTestResults(testResults: Map<String, String>) {
        try {
            val resultText = testResults.entries.joinToString("\n\n") { (aiName, result) ->
                "$aiName:\n$result"
            }

            AlertDialog.Builder(this)
                .setTitle("🧪 连通性测试结果")
                .setMessage(resultText)
                .setPositiveButton("确定", null)
                .show()

        } catch (e: Exception) {
            Log.e(TAG, "显示测试结果失败", e)
        }
    }

    /**
     * 检查是否已有该AI联系人
     */
    private fun hasAIContact(aiName: String): Boolean {
        return try {
            for (category in allContacts) {
                for (contact in category.contacts) {
                    if (contact.name.equals(aiName, ignoreCase = true)) {
                        return true
                    }
                }
            }
            false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 显示重新配置对话框
     */
    private fun showReconfigureDialog(aiName: String, description: String) {
        try {
            val dialogBuilder = AlertDialog.Builder(this)
            dialogBuilder.setTitle("🔄 重新配置")
            dialogBuilder.setMessage("${aiName}已存在，您要执行什么操作？")

            dialogBuilder.setPositiveButton("重新配置API") { _, _ ->
                showAIApiKeyDialog(aiName, description)
            }

            dialogBuilder.setNeutralButton("打开对话") { _, _ ->
                // 找到该AI联系人并打开对话
                for (category in allContacts) {
                    for (contact in category.contacts) {
                        if (contact.name.equals(aiName, ignoreCase = true)) {
                            openChatWithContact(contact)
                            return@setNeutralButton
                        }
                    }
                }
            }

            dialogBuilder.setNegativeButton("取消", null)
            dialogBuilder.show()

        } catch (e: Exception) {
            Log.e(TAG, "显示重新配置对话框失败", e)
        }
    }

    /**
     * 显示AI API密钥输入对话框（带测试功能）
     */
    private fun showAIApiKeyDialog(aiName: String, aiDescription: String) {
        try {
            // 创建自定义布局
            val dialogLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(50, 30, 50, 30)
            }

            // AI信息显示
            val infoText = TextView(this).apply {
                text = "配置 $aiDescription"
                textSize = 16f
                setTextColor(getColor(R.color.simple_mode_text_primary_light))
                setPadding(0, 0, 0, 20)
            }
            dialogLayout.addView(infoText)

            // API密钥输入框
            val apiKeyInput = EditText(this).apply {
                hint = "请输入${aiName}的API密钥"
                inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                setPadding(20, 15, 20, 15)
                background = getDrawable(R.drawable.edit_text_background)
            }
            dialogLayout.addView(apiKeyInput)

            // 添加间距
            val spacer1 = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, 20)
            }
            dialogLayout.addView(spacer1)

            // 测试结果显示
            val testResultText = TextView(this).apply {
                text = "💡 输入API密钥后可测试连通性"
                textSize = 14f
                setTextColor(getColor(R.color.simple_mode_text_secondary_light))
                setPadding(20, 10, 20, 10)
                background = getDrawable(R.drawable.search_box_background)
            }
            dialogLayout.addView(testResultText)

            // 添加间距
            val spacer2 = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, 15)
            }
            dialogLayout.addView(spacer2)

            // 按钮容器
            val buttonLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER
            }

            // 测试连通性按钮
            val testButton = MaterialButton(this).apply {
                text = "🧪 测试连通性"
                setBackgroundColor(getColor(R.color.simple_mode_accent_light))
                setTextColor(getColor(android.R.color.white))
                isEnabled = false
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = 10
                }
            }
            buttonLayout.addView(testButton)

            // 保存按钮
            val saveButton = MaterialButton(this).apply {
                text = "💾 保存配置"
                setBackgroundColor(getColor(R.color.simple_mode_primary_light))
                setTextColor(getColor(android.R.color.white))
                isEnabled = false
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = 10
                }
            }
            buttonLayout.addView(saveButton)

            dialogLayout.addView(buttonLayout)

            // 创建对话框
            val dialog = AlertDialog.Builder(this)
                .setTitle("🔧 配置${aiName}")
                .setView(dialogLayout)
                .setNegativeButton("取消", null)
                .create()

            // 监听API密钥输入
            apiKeyInput.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    val apiKey = s?.toString()?.trim() ?: ""
                    val isValid = isValidApiKey(apiKey, aiName)
                    testButton.isEnabled = isValid
                    saveButton.isEnabled = isValid

                    if (apiKey.isEmpty()) {
                        testResultText.text = "💡 输入API密钥后可测试连通性"
                        testResultText.setTextColor(getColor(R.color.simple_mode_text_secondary_light))
                    } else if (!isValid) {
                        testResultText.text = "❌ API密钥格式不正确"
                        testResultText.setTextColor(getColor(android.R.color.holo_red_dark))
                    } else {
                        testResultText.text = "✅ API密钥格式正确，可以测试连通性"
                        testResultText.setTextColor(getColor(android.R.color.holo_green_dark))
                    }
                }
            })

            // 测试按钮点击事件
            testButton.setOnClickListener {
                val apiKey = apiKeyInput.text.toString().trim()
                testAPIConnection(aiName, apiKey, testResultText)
            }

            // 保存按钮点击事件
            saveButton.setOnClickListener {
                val apiKey = apiKeyInput.text.toString().trim()
                if (isValidApiKey(apiKey, aiName)) {
                    // 保存API密钥
                    saveApiKeyForAI(aiName, apiKey)

                    // 添加AI助手到联系人列表
                    addAIContactToCategory(aiName, aiDescription, apiKey)

                dialog.dismiss()
                    Toast.makeText(this, "${aiName}已成功添加", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "API密钥格式不正确", Toast.LENGTH_SHORT).show()
                }
            }

            dialog.show()

        } catch (e: Exception) {
            Log.e(TAG, "显示AI API密钥对话框失败", e)
            Toast.makeText(this, "显示对话框失败", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 测试API连接
     */
    private fun testAPIConnection(aiName: String, apiKey: String, resultTextView: TextView) {
        try {
            resultTextView.text = "🔄 正在测试连接..."
            resultTextView.setTextColor(getColor(R.color.simple_mode_accent_light))

            // 使用协程进行异步测试
            lifecycleScope.launch {
                try {
                    val result = performAPITest(aiName, apiKey)
                    runOnUiThread {
                        if (result.success) {
                            resultTextView.text = "✅ 连接成功！${result.message}"
                            resultTextView.setTextColor(getColor(android.R.color.holo_green_dark))
                        } else {
                            resultTextView.text = "❌ 连接失败：${result.message}"
                            resultTextView.setTextColor(getColor(android.R.color.holo_red_dark))
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        resultTextView.text = "❌ 测试失败：${e.message}"
                        resultTextView.setTextColor(getColor(android.R.color.holo_red_dark))
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "测试API连接失败", e)
            resultTextView.text = "❌ 测试失败：${e.message}"
            resultTextView.setTextColor(getColor(android.R.color.holo_red_dark))
        }
    }

    /**
     * 执行API测试
     */
    private suspend fun performAPITest(aiName: String, apiKey: String): TestResult {
        return withContext(Dispatchers.IO) {
            try {
                val apiUrl = getDefaultApiUrl(aiName)
                val url = URL(apiUrl)
                val connection = url.openConnection() as HttpURLConnection

                connection.apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Authorization", "Bearer $apiKey")
                    connectTimeout = 10000
                    readTimeout = 10000
                    doOutput = true
                }

                // 构建测试请求
                val testMessage = when (aiName.lowercase()) {
                    "智谱ai" -> """{"model":"glm-4","messages":[{"role":"user","content":"hi"}]}"""
                    "deepseek" -> """{"model":"deepseek-chat","messages":[{"role":"user","content":"hi"}]}"""
                    "kimi" -> """{"model":"moonshot-v1-8k","messages":[{"role":"user","content":"hi"}]}"""
                    else -> """{"model":"gpt-3.5-turbo","messages":[{"role":"user","content":"hi"}]}"""
                }

                connection.outputStream.use { os ->
                    val input = testMessage.toByteArray(Charsets.UTF_8)
                    os.write(input, 0, input.size)
                }

                val responseCode = connection.responseCode
                when (responseCode) {
                    200 -> TestResult(true, "API连接正常")
                    401 -> TestResult(false, "API密钥无效")
                    429 -> TestResult(false, "请求频率过高")
                    500 -> TestResult(false, "服务器内部错误")
                    else -> TestResult(false, "HTTP错误码: $responseCode")
                }

            } catch (e: Exception) {
                when {
                    e.message?.contains("timeout") == true -> TestResult(false, "连接超时")
                    e.message?.contains("UnknownHostException") == true -> TestResult(false, "无法连接到服务器")
                    else -> TestResult(false, e.message ?: "未知错误")
                }
            }
        }
    }

    /**
     * API测试结果数据类
     */
    data class TestResult(val success: Boolean, val message: String)

    /**
     * 显示自定义API对话框
     */
    private fun showCustomAPIDialog() {
        // 复用原来的openAddAPIContactDialog逻辑
        openAddAPIContactDialog()
    }

    /**
     * 加载初始联系人数据
     */
    private fun loadInitialContacts() {
        try {
            // 首先尝试从存储中恢复联系人数据
            val savedContacts = loadSavedContacts()
            if (savedContacts.isNotEmpty()) {
                allContacts = savedContacts.toMutableList()
                
                // 使用统一群聊管理器同步群聊数据
                try {
                    syncGroupChatsFromUnifiedManager()
                } catch (e: Exception) {
                    Log.e(TAG, "从统一管理器同步群聊数据失败", e)
                }
                
                chatContactAdapter?.updateContacts(allContacts)
                Log.d(TAG, "从存储中恢复了 ${savedContacts.size} 个联系人分类")
                return
            }

            // 如果没有保存的数据，则生成默认联系人
            generateDefaultContacts()

        } catch (e: Exception) {
            Log.e(TAG, "加载初始联系人数据失败", e)
            // 出错时生成默认联系人
            generateDefaultContacts()
        }
    }

    /**
     * 从统一群聊管理器同步群聊数据到allContacts
     * 使用新的UnifiedGroupChatManager作为单一数据源
     */
    private fun syncGroupChatsFromUnifiedManager() {
        try {
            val groupChats = unifiedGroupChatManager.getAllGroupChats()
            if (groupChats.isEmpty()) {
                Log.d(TAG, "UnifiedGroupChatManager中没有群聊数据")
                return
            }

            // 收集allContacts中已存在的群聊ID
            val existingGroupIds = mutableSetOf<String>()
            for (category in allContacts) {
                for (contact in category.contacts) {
                    if (contact.type == ContactType.GROUP && contact.groupId != null) {
                        existingGroupIds.add(contact.groupId!!)
                    }
                }
            }

            // 找出需要添加的新群聊
            val newGroupChats = groupChats.filter { !existingGroupIds.contains(it.id) }
            if (newGroupChats.isNotEmpty()) {
                val groupChatContacts = newGroupChats.map { groupChat ->
                    ChatContact(
                        id = groupChat.id,
                        name = groupChat.name,
                        avatar = groupChat.avatar,
                        type = ContactType.GROUP,
                        description = groupChat.description,
                        isOnline = true,
                        lastMessage = groupChat.lastMessage,
                        lastMessageTime = groupChat.lastMessageTime,
                        unreadCount = groupChat.unreadCount,
                        isPinned = groupChat.isPinned,
                        isMuted = groupChat.isMuted,
                        groupId = groupChat.id,
                        memberCount = groupChat.members.size,
                        aiMembers = groupChat.members.filter { member -> member.type == MemberType.AI }.map { member -> member.name }
                    )
                }

                // 添加到第一个分类中（通常是"全部联系人"）
                if (allContacts.isNotEmpty()) {
                    allContacts[0].contacts.addAll(groupChatContacts)
                } else {
                    allContacts.add(ContactCategory("全部联系人", groupChatContacts.toMutableList()))
                }
                
                Log.d(TAG, "从统一管理器同步了 ${newGroupChats.size} 个新群聊到allContacts")
            } else {
                Log.d(TAG, "所有群聊已存在于allContacts中，无需同步")
            }
        } catch (e: Exception) {
            Log.e(TAG, "从统一管理器同步群聊数据失败", e)
        }
    }

    /**
     * 生成默认联系人数据
     */
    private fun generateDefaultContacts() {
        try {
            val settingsManager = SettingsManager.getInstance(this)

            // 定义所有可用的AI助手
            val availableAIs = listOf(
                "DeepSeek" to "DeepSeek的AI助手",
                "ChatGPT" to "OpenAI的AI助手",
                "Claude" to "Anthropic的AI助手",
                "Gemini" to "Google的AI助手",
                "智谱AI" to "智谱AI的GLM-4大语言模型",
                "文心一言" to "百度的大语言模型",
                "通义千问" to "阿里巴巴的大语言模型",
                "讯飞星火" to "科大讯飞的AI助手",
                "Kimi" to "Moonshot的AI助手"
            )

            val aiContacts = mutableListOf<ChatContact>()

            // 检查每个AI是否有有效的API密钥配置，只添加有配置的AI
            availableAIs.forEach { (aiName, description) ->
                val apiKey = getApiKeyForAI(aiName)
                if (isValidApiKey(apiKey, aiName)) {
                    // 有有效API密钥配置，添加到联系人列表
                    // 获取真实的最后聊天消息
                    val lastChatMessage = getLastChatMessageFromHistory(aiName)
                    val lastChatTime = getLastChatTimeFromHistory(aiName)
                    
                    // 使用与灵动岛相同的ID生成逻辑
                    val processedName = if (aiName.contains(Regex("[\\u4e00-\\u9fff]"))) {
                        // 包含中文字符，直接使用原名称
                        aiName
                    } else {
                        // 英文字符，转换为小写
                        aiName.lowercase()
                    }
                    val contactId = "ai_${processedName.replace(" ", "_")}"
                    
                    Log.d(TAG, "生成AI联系人 - AI名称: $aiName, 联系人ID: $contactId")
                    
                    val contact = ChatContact(
                        id = contactId,
                        name = aiName,
                        type = ContactType.AI,
                        description = description,
                        isOnline = true,
                        lastMessage = lastChatMessage.ifEmpty { "你好！我是$aiName，有什么可以帮助你的吗？" },
                        lastMessageTime = if (lastChatTime > 0) lastChatTime else System.currentTimeMillis() - (Math.random() * 86400000).toLong(),
                        unreadCount = 0,
                        isPinned = true, // 有API配置的AI优先显示
                        customData = mapOf(
                            "api_url" to getDefaultApiUrl(aiName),
                            "api_key" to apiKey,
                            "model" to getDefaultModel(aiName)
                        ),
                        aiMembers = emptyList()
                    )
                    aiContacts.add(contact)
                    Log.d(TAG, "添加AI助手: $aiName (API密钥已配置)")
                } else {
                    Log.d(TAG, "跳过AI助手: $aiName (API密钥未配置或无效)")
                }
            }

            // 如果没有配置任何AI，显示提示信息
            if (aiContacts.isEmpty()) {
                Log.d(TAG, "没有配置任何AI助手，显示空状态")
            }

            // 从UnifiedGroupChatManager加载群聊数据（仅在生成默认数据时）
            val groupChatContacts = mutableListOf<ChatContact>()
            try {
                val groupChats = unifiedGroupChatManager.getAllGroupChats()
                if (groupChats.isNotEmpty()) {
                    groupChats.forEach { groupChat ->
                        val groupChatContact = ChatContact(
                            id = groupChat.id,
                            name = groupChat.name,
                            avatar = groupChat.avatar,
                            type = ContactType.GROUP,
                            description = groupChat.description,
                            isOnline = true,
                            lastMessage = groupChat.lastMessage,
                            lastMessageTime = groupChat.lastMessageTime,
                            unreadCount = groupChat.unreadCount,
                            isPinned = groupChat.isPinned,
                            isMuted = groupChat.isMuted,
                            groupId = groupChat.id,
                            memberCount = groupChat.members.size,
                            aiMembers = groupChat.members.filter { it.type == MemberType.AI }.map { it.name }
                        )
                        groupChatContacts.add(groupChatContact)
                    }
                    Log.d(TAG, "生成默认数据时从UnifiedGroupChatManager加载了 ${groupChats.size} 个群聊")
                }
            } catch (e: Exception) {
                Log.e(TAG, "从统一管理器加载群聊数据失败", e)
            }
            
            // 创建分类，包含AI助手和群聊
            val allChatContacts = mutableListOf<ChatContact>()
            allChatContacts.addAll(aiContacts)
            allChatContacts.addAll(groupChatContacts)
            
            allContacts = mutableListOf(
                ContactCategory("全部联系人", allChatContacts)
            )

            // 保存生成的联系人数据
            saveContacts()

            // 更新适配器
            chatContactAdapter?.updateContacts(allContacts)
            Log.d(TAG, "初始联系人数据生成完成")
        } catch (e: Exception) {
            Log.e(TAG, "生成默认联系人数据失败", e)
        }
    }

    /**
     * 打开与联系人的聊天
     */
    private fun openChatWithContact(contact: ChatContact) {
        try {
            // 在进入AI对话前，强制加载AI联系人列表数据汇总
            if (contact.type == ContactType.AI) {
                forceLoadContactDataSummary()
            }
            
            val intent = Intent(this, ChatActivity::class.java)
            intent.putExtra(ChatActivity.EXTRA_CONTACT, contact)
            startActivity(intent)
            Log.d(TAG, "打开与联系人的聊天: ${contact.name}")
        } catch (e: Exception) {
            Log.e(TAG, "打开聊天失败", e)
            Toast.makeText(this, "打开聊天失败", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 显示联系人选项对话框（直接显示分组选项）
     */
    private fun showContactOptionsDialog(contact: ChatContact) {
        try {
            Log.d(TAG, "显示联系人选项对话框: ${contact.name}, 类型: ${contact.type}")

            val options = mutableListOf<String>()
            val currentGroup = findContactGroup(contact)

            // 为AI联系人直接添加所有可用分组选项
            if (contact.type == ContactType.AI) {
                // 添加分组转移选项
                addGroupTransferOptions(options, contact, currentGroup)

                // 添加分隔线
                options.add("─────────────────")
            }

            // 根据当前状态动态添加其他选项
            if (contact.isPinned) {
                options.add("取消置顶")
            } else {
                options.add("置顶")
            }

            if (contact.isMuted) {
                options.add("取消静音")
            } else {
                options.add("静音")
            }

            options.add("删除")

            Log.d(TAG, "菜单选项: ${options.joinToString(", ")}")

            // 构建对话框标题
            val title = if (currentGroup != null && currentGroup != "未分组") {
                "${contact.name} (当前分组: $currentGroup)"
            } else {
                "${contact.name} (当前在: 全部)"
            }

            AlertDialog.Builder(this, R.style.Theme_MaterialDialog)
                .setTitle(title)
                .setItems(options.toTypedArray()) { _, which ->
                    handleContactOptionSelection(contact, options, which, currentGroup)
                }
                .setNegativeButton("取消") { _, _ ->
                    Log.d(TAG, "用户取消了操作")
                }
                .show()

            Log.d(TAG, "联系人选项对话框已显示，共 ${options.size} 个选项")

        } catch (e: Exception) {
            Log.e(TAG, "显示联系人选项对话框失败", e)
            Toast.makeText(this, "无法显示菜单", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 添加分组转移选项到菜单中
     */
    private fun addGroupTransferOptions(options: MutableList<String>, contact: ChatContact, currentGroup: String?) {
        try {
            // 如果AI在某个分组中，添加"移除分组"选项
            if (currentGroup != null && currentGroup != "未分组" && currentGroup != "全部") {
                options.add("🔄 移除分组（回到全部）")
            }

            // 确保"AI助手"分组存在
            ensureAIAssistantGroupExists()

            // 添加"AI助手"分组（如果当前不在此分组）
            if (currentGroup != "AI助手") {
                options.add("📁 移动到: AI助手")
            }

            // 添加所有现有的自定义分组
            allContacts.forEach { category ->
                if (category.name != "AI助手" &&
                    category.name != currentGroup &&
                    category.name != "未分组") {
                    options.add("📁 移动到: ${category.name}")
                }
            }

            // 从标签页获取额外的分组
            val chatTabLayout = findViewById<com.google.android.material.tabs.TabLayout>(R.id.chat_tab_layout)
            for (i in 2 until (chatTabLayout?.tabCount ?: 0)) {
                val tab = chatTabLayout?.getTabAt(i)
                val tabText = tab?.text?.toString()
                if (tabText != null &&
                    tabText != "+" &&
                    tabText != currentGroup &&
                    !options.any { it.contains(tabText) }) {
                    options.add("📁 移动到: $tabText")
                }
            }

            // 如果分组选项太少，添加一些常用分组
            val groupCount = options.count { it.startsWith("📁 移动到:") }
            if (groupCount < 3) {
                val defaultGroups = listOf("工作AI", "学习AI", "生活AI", "娱乐AI")
                defaultGroups.forEach { groupName ->
                    if (groupName != currentGroup && !options.any { it.contains(groupName) }) {
                        options.add("📁 移动到: $groupName")
                    }
                }
            }

            // 总是添加"创建新分组"选项
            options.add("➕ 创建新分组")

            Log.d(TAG, "添加了 ${options.count { it.startsWith("📁") || it.startsWith("🔄") || it.startsWith("➕") }} 个分组选项")

        } catch (e: Exception) {
            Log.e(TAG, "添加分组转移选项失败", e)
        }
    }



    /**
     * 处理联系人选项选择（支持直接分组转移）
     */
    private fun handleContactOptionSelection(contact: ChatContact, options: List<String>, which: Int, currentGroup: String?) {
        try {
            if (which < 0 || which >= options.size) {
                Log.e(TAG, "选择索引超出范围: $which")
                return
            }

            val selectedOption = options[which]
            Log.d(TAG, "用户选择了选项: $selectedOption")

            when {
                selectedOption == "🔄 移除分组（回到全部）" -> {
                    Log.d(TAG, "执行移除分组操作")
                    confirmRemoveFromGroup(contact, currentGroup)
                }
                selectedOption.startsWith("📁 移动到: ") -> {
                    val targetGroup = selectedOption.removePrefix("📁 移动到: ")
                    Log.d(TAG, "执行移动到分组操作: $targetGroup")
                    confirmMoveToGroup(contact, targetGroup, currentGroup)
                }
                selectedOption == "➕ 创建新分组" -> {
                    Log.d(TAG, "执行创建新分组操作")
                    showCreateNewGroupForContactDialog(contact)
                }
                selectedOption == "─────────────────" -> {
                    // 分隔线，忽略
                    return
                }
                selectedOption.contains("置顶") -> {
                    val isPinning = !selectedOption.contains("取消")
                    Log.d(TAG, "执行${if (isPinning) "置顶" else "取消置顶"}")
                    toggleContactPin(contact, isPinning)
                }
                selectedOption.contains("静音") -> {
                    val isMuting = !selectedOption.contains("取消")
                    Log.d(TAG, "执行${if (isMuting) "静音" else "取消静音"}")
                    toggleContactMute(contact, isMuting)
                }
                selectedOption == "删除" -> {
                    Log.d(TAG, "执行删除")
                    confirmDeleteContact(contact)
                }
                else -> {
                    Log.w(TAG, "未识别的选项: $selectedOption")
                    Toast.makeText(this, "未识别的操作", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "执行菜单选项失败", e)
            Toast.makeText(this, "操作失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 确认移除分组
     */
    private fun confirmRemoveFromGroup(contact: ChatContact, currentGroup: String?) {
        val message = "确定要将 ${contact.name} 从分组 \"$currentGroup\" 中移除吗？\n\n移除后AI将回到\"全部\"标签页中。"

        AlertDialog.Builder(this, R.style.Theme_MaterialDialog)
            .setTitle("确认移除分组")
            .setMessage(message)
            .setPositiveButton("确定移除") { _, _ ->
                removeContactFromGroup(contact)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 确认移动到分组
     */
    private fun confirmMoveToGroup(contact: ChatContact, targetGroup: String, currentGroup: String?) {
        val message = if (currentGroup != null && currentGroup != "未分组") {
            "确定要将 ${contact.name} 从 \"$currentGroup\" 移动到 \"$targetGroup\" 吗？"
        } else {
            "确定要将 ${contact.name} 移动到分组 \"$targetGroup\" 吗？"
        }

        AlertDialog.Builder(this, R.style.Theme_MaterialDialog)
            .setTitle("确认移动")
            .setMessage(message)
            .setPositiveButton("确定移动") { _, _ ->
                moveContactToGroup(contact, targetGroup)
            }
            .setNegativeButton("取消", null)
            .show()
    }



    /**
     * 确认删除联系人
     */
    private fun confirmDeleteContact(contact: ChatContact) {
        when (contact.type) {
            ContactType.AI -> {
                AlertDialog.Builder(this, R.style.Theme_MaterialDialog)
                    .setTitle("移除AI助手")
                    .setMessage("确定要移除 ${contact.name} 吗？\n\n这将清除该AI的API配置，但可以重新添加。")
                    .setPositiveButton("移除") { _, _ ->
                        removeAIConfiguration(contact)
                        Toast.makeText(this, "${contact.name} 配置已移除", Toast.LENGTH_SHORT).show()
                        Log.d(TAG, "移除AI配置: ${contact.name}")
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
            ContactType.GROUP -> {
                AlertDialog.Builder(this, R.style.Theme_MaterialDialog)
                    .setTitle("删除群聊")
                    .setMessage("确定要删除群聊 ${contact.name} 吗？\n\n删除后将无法恢复，包括群聊中的所有消息。")
                    .setPositiveButton("删除") { _, _ ->
                        removeGroupChatConfiguration(contact)
                        Log.d(TAG, "删除群聊: ${contact.name}")
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
            else -> {
                AlertDialog.Builder(this, R.style.Theme_MaterialDialog)
                    .setTitle("删除联系人")
                    .setMessage("确定要删除 ${contact.name} 吗？\n\n删除后将无法恢复。")
                    .setPositiveButton("删除") { _, _ ->
                        removeContactFromList(contact)
                        Toast.makeText(this, "${contact.name} 已删除", Toast.LENGTH_SHORT).show()
                        Log.d(TAG, "删除联系人: ${contact.name}")
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
        }
    }

    /**
     * 测试群聊删除功能（调试用）
     */
    private fun testGroupChatDeleteFunction() {
        try {
            Log.d(TAG, "=== 开始测试群聊删除功能 ===")
            
            // 查找第一个群聊联系人
            val groupContact = allContacts.flatMap { it.contacts }
                .firstOrNull { it.type == ContactType.GROUP }
            
            if (groupContact != null) {
                Log.d(TAG, "找到群聊联系人: ${groupContact.name} (ID: ${groupContact.id}, GroupID: ${groupContact.groupId})")
                
                // 测试删除确认对话框
                confirmDeleteContact(groupContact)
                
                Log.d(TAG, "群聊删除测试对话框已显示")
            } else {
                Log.w(TAG, "没有找到群聊联系人进行测试")
                Toast.makeText(this, "没有找到群聊进行测试", Toast.LENGTH_SHORT).show()
            }
            
            Log.d(TAG, "=== 群聊删除功能测试完成 ===")
        } catch (e: Exception) {
            Log.e(TAG, "测试群聊删除功能失败", e)
            Toast.makeText(this, "测试失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 测试长按菜单功能（调试用）
     */
    private fun testContactOptionsMenu() {
        try {
            Log.d(TAG, "=== 开始测试长按菜单功能 ===")

            // 找一个AI联系人进行测试
            val testContact = allContacts.flatMap { it.contacts }
                .firstOrNull { it.type == ContactType.AI }

            if (testContact != null) {
                Log.d(TAG, "使用测试联系人: ${testContact.name}")

                // 模拟构建菜单选项
                val options = mutableListOf<String>()
                val currentGroup = findContactGroup(testContact)

                Log.d(TAG, "测试联系人当前分组: $currentGroup")

                // 测试添加分组选项
                addGroupTransferOptions(options, testContact, currentGroup)

                Log.d(TAG, "生成的菜单选项:")
                options.forEachIndexed { index, option ->
                    Log.d(TAG, "  [$index] $option")
                }

                Log.d(TAG, "菜单功能测试完成，共 ${options.size} 个选项")
            } else {
                Log.w(TAG, "未找到AI联系人进行测试")
            }

            Log.d(TAG, "=== 长按菜单功能测试完成 ===")

        } catch (e: Exception) {
            Log.e(TAG, "测试长按菜单功能失败", e)
        }
    }

    /**
     * 验证分组转移功能的完整性
     */
    private fun validateGroupTransferFunction(): Boolean {
        try {
            // 检查必要的UI组件
            val chatTabLayout = findViewById<com.google.android.material.tabs.TabLayout>(R.id.chat_tab_layout)
            if (chatTabLayout == null) {
                Log.e(TAG, "chatTabLayout为null")
                return false
            }

            // 检查适配器
            if (chatContactAdapter == null) {
                Log.e(TAG, "chatContactAdapter为null")
                return false
            }

            // 检查数据结构
            if (allContacts.isEmpty()) {
                Log.w(TAG, "allContacts为空")
                return false
            }

            Log.d(TAG, "分组转移功能验证通过")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "验证分组转移功能失败", e)
            return false
        }
    }

    /**
     * 显示AI分组选择对话框（优化版）
     */
    private fun showAIGroupSelectionDialog(contact: ChatContact) {
        try {
            Log.d(TAG, "显示AI分组选择对话框: ${contact.name}")

            // 获取当前AI所在的分组
            val currentGroup = findContactGroup(contact)
            Log.d(TAG, "当前分组: $currentGroup")

            // 获取所有可用的分组选项
            val availableGroups = buildAvailableGroupsList(contact, currentGroup)

            if (availableGroups.isEmpty()) {
                Toast.makeText(this, "无可用分组，请先创建分组", Toast.LENGTH_SHORT).show()
                showCreateNewGroupForContactDialog(contact)
                return
            }

            Log.d(TAG, "可用分组: ${availableGroups.joinToString(", ")}")

            // 构建对话框标题和消息
            val title = "移动 ${contact.name}"
            val message = buildGroupSelectionMessage(currentGroup)

            // 显示分组选择对话框
            showGroupSelectionDialog(contact, title, message, availableGroups, currentGroup)

        } catch (e: Exception) {
            Log.e(TAG, "显示AI分组选择对话框失败", e)
            Toast.makeText(this, "无法显示分组选择，使用简化版本", Toast.LENGTH_SHORT).show()
            showSimpleGroupSelectionDialog(contact)
        }
    }

    /**
     * 构建可用分组列表
     */
    private fun buildAvailableGroupsList(contact: ChatContact, currentGroup: String?): MutableList<String> {
        val availableGroups = mutableListOf<String>()

        // 1. 如果AI在某个分组中，添加"移除分组"选项
        if (currentGroup != null && currentGroup != "未分组" && currentGroup != "全部") {
            availableGroups.add("🔄 移除分组（回到全部）")
        }

        // 2. 确保"AI助手"分组存在
        ensureAIAssistantGroupExists()

        // 3. 添加"AI助手"分组（如果当前不在此分组）
        if (currentGroup != "AI助手") {
            availableGroups.add("AI助手")
        }

        // 4. 只添加实际存在且有效的分组（排除当前分组）
        val validGroups = getValidExistingGroups()
        validGroups.forEach { groupName ->
            if (groupName != "AI助手" &&
                groupName != currentGroup && // 确保不添加当前分组
                groupName != "未分组" &&
                groupName != "全部" &&
                !availableGroups.contains(groupName)) {
                availableGroups.add(groupName)
                Log.d(TAG, "添加有效分组选项: $groupName")
            }
        }

        // 5. 如果分组太少，添加一些预设分组（排除当前分组）
        if (availableGroups.size <= 1) {
            addDefaultGroups(availableGroups, currentGroup)
            Log.d(TAG, "添加默认分组后，可用选项数: ${availableGroups.size}")
        }

        // 6. 总是添加"创建新分组"选项
        availableGroups.add("+ 创建新分组")

        return availableGroups
    }

    /**
     * 获取实际存在的有效分组列表
     */
    private fun getValidExistingGroups(): List<String> {
        val validGroups = mutableListOf<String>()

        // 从TabLayout获取当前显示的标签页（这些是实际存在的分组）
        val chatTabLayout = findViewById<com.google.android.material.tabs.TabLayout>(R.id.chat_tab_layout)
        chatTabLayout?.let { tabLayout ->
            for (i in 0 until tabLayout.tabCount) {
                val tab = tabLayout.getTabAt(i)
                val tabText = tab?.text?.toString()
                if (tabText != null &&
                    tabText != "+" &&
                    tabText != "全部" &&
                    tabText != "未分组" &&
                    !validGroups.contains(tabText)) {
                    validGroups.add(tabText)
                }
            }
        }

        // 同时检查allContacts中确实存在对应的分组数据
        val finalValidGroups = validGroups.filter { groupName ->
            allContacts.any { it.name == groupName }
        }

        Log.d(TAG, "有效分组列表: ${finalValidGroups.joinToString(", ")}")
        return finalValidGroups
    }

    /**
     * 确保AI助手分组存在（仅在用户未主动删除时）
     */
    private fun ensureAIAssistantGroupExists() {
        // 检查用户是否主动删除了AI助手分组
        val prefs = getSharedPreferences("custom_tabs", MODE_PRIVATE)
        val isAIAssistantGroupDeleted = prefs.getBoolean("ai_assistant_group_deleted", false)

        if (!isAIAssistantGroupDeleted) {
            val aiAssistantCategory = allContacts.find { it.name == "AI助手" }
            if (aiAssistantCategory == null) {
                val newAIAssistantCategory = ContactCategory(
                    name = "AI助手",
                    contacts = mutableListOf(),
                    isExpanded = true
                )
                allContacts.add(newAIAssistantCategory)
                saveContacts()
                Log.d(TAG, "创建了缺失的AI助手分组")
            }
        }
    }



    /**
     * 添加默认分组选项（仅在移动AI时使用，不会创建实际的空分组）
     */
    private fun addDefaultGroups(availableGroups: MutableList<String>, currentGroup: String?) {
        // 只有在分组选项非常少的情况下才添加默认分组建议
        if (availableGroups.size <= 1) {
            // 固定的5个默认分组建议
            val defaultGroups = listOf("工作AI", "学习AI", "生活AI", "娱乐AI", "创作AI")
            defaultGroups.forEach { groupName ->
                if (groupName != currentGroup && !availableGroups.contains(groupName)) {
                    availableGroups.add(groupName)
                }
            }
            Log.d(TAG, "添加了 ${defaultGroups.size} 个默认分组选项作为建议")
        }
    }

    /**
     * 构建分组选择消息
     */
    private fun buildGroupSelectionMessage(currentGroup: String?): String {
        return when {
            currentGroup != null && currentGroup != "未分组" && currentGroup != "全部" ->
                "📁 当前分组：$currentGroup\n\n请选择要移动到的目标分组："
            else ->
                "📋 当前在\"全部\"中\n\n请选择要移动到的分组："
        }
    }

    /**
     * 显示分组选择对话框
     */
    private fun showGroupSelectionDialog(
        contact: ChatContact,
        title: String,
        message: String,
        availableGroups: List<String>,
        currentGroup: String?
    ) {
        val dialog = AlertDialog.Builder(this, R.style.Theme_MaterialDialog)
            .setTitle(title)
            .setMessage(message)
            .setItems(availableGroups.toTypedArray()) { _, which ->
                handleGroupSelection(contact, availableGroups, which, currentGroup)
            }
            .setNegativeButton("取消") { dialog, _ ->
                Log.d(TAG, "用户取消了分组选择")
                dialog.dismiss()
            }
            .setNeutralButton("帮助") { _, _ ->
                showGroupSelectionHelp()
            }
            .create()

        dialog.show()
        Log.d(TAG, "分组选择对话框已显示，共 ${availableGroups.size} 个选项")
    }

    /**
     * 处理分组选择
     */
    private fun handleGroupSelection(
        contact: ChatContact,
        availableGroups: List<String>,
        which: Int,
        currentGroup: String?
    ) {
        try {
            if (which < 0 || which >= availableGroups.size) {
                Log.e(TAG, "选择索引超出范围: $which, 可用选项数: ${availableGroups.size}")
                Toast.makeText(this, "选择无效，请重试", Toast.LENGTH_SHORT).show()
                return
            }

            val selectedGroup = availableGroups[which]
            Log.d(TAG, "用户选择的分组: $selectedGroup (索引: $which)")

            when {
                selectedGroup == "🔄 移除分组（回到全部）" -> {
                    Log.d(TAG, "执行移除分组操作")
                    removeContactFromGroup(contact)
                }
                selectedGroup == "+ 创建新分组" -> {
                    Log.d(TAG, "执行创建新分组操作")
                    showCreateNewGroupForContactDialog(contact)
                }
                selectedGroup == currentGroup -> {
                    Log.d(TAG, "AI已在目标分组中，不应该出现这种情况")
                    Toast.makeText(this, "${contact.name} 已在分组 \"$selectedGroup\" 中", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    Log.d(TAG, "执行移动到分组操作: $selectedGroup")
                    confirmAndMoveToGroup(contact, selectedGroup, currentGroup)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "处理分组选择失败", e)
            Toast.makeText(this, "操作失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 确认并移动到分组
     */
    private fun confirmAndMoveToGroup(contact: ChatContact, targetGroup: String, currentGroup: String?) {
        val message = if (currentGroup != null && currentGroup != "未分组") {
            "确定要将 ${contact.name} 从 \"$currentGroup\" 移动到 \"$targetGroup\" 吗？"
        } else {
            "确定要将 ${contact.name} 移动到分组 \"$targetGroup\" 吗？"
        }

        AlertDialog.Builder(this, R.style.Theme_MaterialDialog)
            .setTitle("确认移动")
            .setMessage(message)
            .setPositiveButton("确定") { _, _ ->
                moveContactToGroup(contact, targetGroup)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 显示分组选择帮助
     */
    private fun showGroupSelectionHelp() {
        val helpMessage = """
            📖 分组功能说明：

            🔄 移除分组：将AI从当前分组移除，回到"全部"标签页

            📁 现有分组：选择已创建的分组进行移动

            ➕ 创建新分组：创建一个新的分组并移动AI到该分组

            💡 提示：
            • 移动后AI会出现在对应的标签页中
            • 可以随时重新移动AI到其他分组
            • 删除分组时，其中的AI会自动回到"全部"中
        """.trimIndent()

        AlertDialog.Builder(this, R.style.Theme_MaterialDialog)
            .setTitle("分组功能帮助")
            .setMessage(helpMessage)
            .setPositiveButton("知道了", null)
            .show()
    }

    /**
     * 显示简单的分组选择对话框（备用方案）
     */
    private fun showSimpleGroupSelectionDialog(contact: ChatContact) {
        try {
            Log.d(TAG, "显示简单分组选择对话框（备用方案）")

            // 获取当前分组
            val currentGroup = findContactGroup(contact)

            // 构建基本选项
            val basicOptions = mutableListOf<String>()

            // 添加移除分组选项（如果适用）
            if (currentGroup != null && currentGroup != "未分组" && currentGroup != "全部") {
                basicOptions.add("🔄 移除分组（回到全部）")
            }

            // 添加基本分组选项
            val defaultGroups = listOf("AI助手", "工作AI", "学习AI", "生活AI", "娱乐AI")
            defaultGroups.forEach { groupName ->
                if (groupName != currentGroup) {
                    basicOptions.add(groupName)
                }
            }

            // 添加现有自定义分组
            allContacts.forEach { category ->
                if (!defaultGroups.contains(category.name) &&
                    category.name != currentGroup &&
                    category.name != "未分组" &&
                    !basicOptions.contains(category.name)) {
                    basicOptions.add(category.name)
                }
            }

            // 添加创建新分组选项
            basicOptions.add("+ 创建新分组")

            val message = if (currentGroup != null && currentGroup != "未分组") {
                "当前分组：$currentGroup\n\n选择要移动到的分组："
            } else {
                "当前在\"全部\"中\n\n选择要移动到的分组："
            }

            AlertDialog.Builder(this, R.style.Theme_MaterialDialog)
                .setTitle("移动 ${contact.name}")
                .setMessage(message)
                .setItems(basicOptions.toTypedArray()) { _, which ->
                    try {
                        val selectedOption = basicOptions[which]
                        Log.d(TAG, "简单对话框选择: $selectedOption")

                        when (selectedOption) {
                            "🔄 移除分组（回到全部）" -> {
                                removeContactFromGroup(contact)
                            }
                            "+ 创建新分组" -> {
                                showCreateNewGroupForContactDialog(contact)
                            }
                            else -> {
                                // 确认移动
                                AlertDialog.Builder(this@SimpleModeActivity, R.style.Theme_MaterialDialog)
                                    .setTitle("确认移动")
                                    .setMessage("确定要将 ${contact.name} 移动到 \"$selectedOption\" 吗？")
                                    .setPositiveButton("确定") { _, _ ->
                                        moveContactToGroup(contact, selectedOption)
                                    }
                                    .setNegativeButton("取消", null)
                                    .show()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "处理简单对话框选择失败", e)
                        Toast.makeText(this@SimpleModeActivity, "操作失败", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("取消", null)
                .show()

            Log.d(TAG, "简单分组选择对话框已显示，共 ${basicOptions.size} 个选项")

        } catch (e: Exception) {
            Log.e(TAG, "显示简单分组选择对话框失败", e)
            Toast.makeText(this, "无法显示分组选择对话框", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 显示创建新分组对话框
     */
    private fun showCreateNewGroupDialog(contact: ChatContact) {
        try {
            val input = android.widget.EditText(this).apply {
                hint = "输入新分组名称"
                setPadding(50, 30, 50, 30)
            }

            AlertDialog.Builder(this, R.style.Theme_MaterialDialog)
                .setTitle("创建新分组")
                .setView(input)
                .setPositiveButton("创建") { _, _ ->
                    val groupName = input.text.toString().trim()
                    if (groupName.isNotEmpty()) {
                        moveContactToGroup(contact, groupName)
                    } else {
                        Toast.makeText(this, "分组名称不能为空", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("取消", null)
                .show()

        } catch (e: Exception) {
            Log.e(TAG, "显示创建新分组对话框失败", e)
        }
    }



    /**
     * 切换联系人置顶状态
     */
    private fun toggleContactPin(contact: ChatContact?, isPinned: Boolean) {
        try {
            // 首先检查contact是否为null
            if (contact == null) {
                Log.e(TAG, "联系人对象为null，无法执行置顶操作")
                Toast.makeText(this, "置顶操作失败：联系人数据为空", Toast.LENGTH_SHORT).show()
                return
            }
            
            Log.d(TAG, "开始切换联系人置顶状态: ${contact.name} -> $isPinned")
            Log.d(TAG, "联系人ID: ${contact.id}, 当前置顶状态: ${contact.isPinned}")
            Log.d(TAG, "当前allContacts大小: ${allContacts.size}")
            
            // 验证输入参数
            if (contact.id.isBlank()) {
                Log.e(TAG, "联系人ID为空，无法执行置顶操作")
                Toast.makeText(this, "置顶操作失败：联系人数据异常", Toast.LENGTH_SHORT).show()
                return
            }
            
            // 验证联系人的关键属性
            if (contact.name.isBlank()) {
                Log.e(TAG, "联系人名称为空，无法执行置顶操作")
                Toast.makeText(this, "置顶操作失败：联系人名称异常", Toast.LENGTH_SHORT).show()
                return
            }
            
            // 打印当前所有分类和联系人信息
            allContacts.forEachIndexed { index, category ->
                Log.d(TAG, "分类[$index]: ${category.name}, 联系人数量: ${category.contacts.size}")
                category.contacts.forEach { c ->
                    Log.d(TAG, "  联系人: ${c.name} (id: ${c.id}, isPinned: ${c.isPinned})")
                }
            }

            // 找到联系人所在的分类和位置
            var found = false
            for (categoryIndex in allContacts.indices) {
                val category = allContacts[categoryIndex]
                val contactIndex = category.contacts.indexOfFirst { it.id == contact.id }

                if (contactIndex != -1) {
                    Log.d(TAG, "在分类[${category.name}]中找到联系人: ${contact.name}")
                    val originalContact = category.contacts[contactIndex]
                    Log.d(TAG, "原始联系人状态: isPinned=${originalContact.isPinned}, 目标状态: isPinned=$isPinned")
                    
                    // 检查是否需要更新
                    if (originalContact.isPinned == isPinned) {
                        Log.w(TAG, "联系人${contact.name}已经是${if (isPinned) "置顶" else "非置顶"}状态，无需更新")
                        val action = if (isPinned) "已置顶" else "已取消置顶"
                        Toast.makeText(this, "${contact.name} $action", Toast.LENGTH_SHORT).show()
                        return
                    }
                    
                    val updatedContacts = category.contacts.toMutableList()
                    
                    // 安全地创建更新后的联系人对象
                    val updatedContact = try {
                        contact.copy(
                            id = contact.id ?: "",
                            name = contact.name ?: "未知联系人",
                            type = contact.type,
                            isPinned = isPinned,
                            aiMembers = contact.aiMembers ?: emptyList(),
                            customData = contact.customData ?: emptyMap()
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "创建更新联系人对象失败: ${e.message}")
                        Toast.makeText(this, "置顶操作失败：数据更新异常", Toast.LENGTH_SHORT).show()
                        return
                    }
                    
                    updatedContacts[contactIndex] = updatedContact
                    Log.d(TAG, "更新后的联系人: ${updatedContact.name}, isPinned=${updatedContact.isPinned}")

                    // 重新排序：置顶的联系人在前面
                    val sortedContacts = updatedContacts.sortedWith(
                        compareByDescending<ChatContact> { it.isPinned }
                            .thenByDescending { it.lastMessageTime }
                    ).toMutableList()
                    
                    Log.d(TAG, "排序后联系人列表:")
                    sortedContacts.forEachIndexed { idx, c ->
                        Log.d(TAG, "  [$idx] ${c.name} (isPinned: ${c.isPinned})")
                    }

                    allContacts[categoryIndex] = category.copy(contacts = sortedContacts)
                    found = true
                    break
                }
            }

            if (!found) {
                // 如果在allContacts中没找到，根据联系人类型添加到相应分类
                val targetCategory = if (contact.type == ContactType.AI) "AI助手" else "未分组"
                Log.d(TAG, "在allContacts中未找到联系人，尝试添加到$targetCategory")
                
                // 安全地创建更新后的联系人对象
                val updatedContact = try {
                    contact.copy(
                        id = contact.id ?: "",
                        name = contact.name ?: "未知联系人",
                        type = contact.type,
                        isPinned = isPinned,
                        aiMembers = contact.aiMembers ?: emptyList(),
                        customData = contact.customData ?: emptyMap()
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "创建新联系人对象失败: ${e.message}")
                    Toast.makeText(this, "置顶操作失败：数据创建异常", Toast.LENGTH_SHORT).show()
                    return
                }

                // 查找或创建目标类别
                val categoryIndex = allContacts.indexOfFirst { it.name == targetCategory }
                if (categoryIndex != -1) {
                    val category = allContacts[categoryIndex]
                    val updatedContacts = category.contacts.toMutableList()
                    updatedContacts.add(updatedContact)

                    val sortedContacts = updatedContacts.sortedWith(
                        compareByDescending<ChatContact> { it.isPinned }
                            .thenByDescending { it.lastMessageTime }
                    ).toMutableList()

                    allContacts[categoryIndex] = category.copy(contacts = sortedContacts)
                } else {
                    // 创建新的类别
                    val newCategory = ContactCategory(
                        name = targetCategory,
                        contacts = mutableListOf(updatedContact),
                        isExpanded = true
                    )
                    if (targetCategory == "AI助手") {
                        allContacts.add(newCategory)
                    } else {
                        allContacts.add(0, newCategory)
                    }
                }
                found = true
            }

            if (found) {
                Log.d(TAG, "开始保存联系人数据...")
                // 保存更新后的联系人数据
                saveContacts()
                Log.d(TAG, "联系人数据保存完成")

                Log.d(TAG, "开始刷新UI显示...")
                // 立即刷新显示
                refreshCurrentTabDisplay()
                Log.d(TAG, "UI显示刷新完成")

                // 更新适配器
                if (chatContactAdapter != null) {
                    Log.d(TAG, "更新聊天联系人适配器...")
                    chatContactAdapter?.updateContacts(allContacts)
                    Log.d(TAG, "聊天联系人适配器更新完成")
                } else {
                    Log.w(TAG, "聊天联系人适配器为null，跳过更新")
                }

                val action = if (isPinned) "已置顶" else "已取消置顶"
                Toast.makeText(this, "${contact.name} $action", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "切换联系人置顶状态成功: ${contact.name} -> $isPinned")
                
                // 验证最终状态
                val finalContact = findContactById(contact.id)
                if (finalContact != null) {
                    Log.d(TAG, "最终验证 - 联系人${finalContact.name}的置顶状态: ${finalContact.isPinned}")
                } else {
                    Log.w(TAG, "最终验证失败 - 无法找到联系人${contact.name}")
                }
            } else {
                Log.e(TAG, "无法找到联系人: ${contact.name}")
                Toast.makeText(this, "操作失败：找不到联系人", Toast.LENGTH_SHORT).show()
            }

        } catch (e: Exception) {
            Log.e(TAG, "切换联系人置顶状态失败", e)
            Log.e(TAG, "异常详情: ${e.message}")
            Log.e(TAG, "异常堆栈: ${e.stackTraceToString()}")
            Toast.makeText(this, "置顶操作失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * 根据ID查找联系人
     */
    private fun findContactById(contactId: String): ChatContact? {
        for (category in allContacts) {
            for (contact in category.contacts) {
                if (contact.id == contactId) {
                    return contact
                }
            }
        }
        return null
    }

    /**
     * 切换联系人静音状态
     */
    private fun toggleContactMute(contact: ChatContact?, isMuted: Boolean) {
        try {
            // 首先检查contact是否为null
            if (contact == null) {
                Log.e(TAG, "联系人对象为null，无法执行静音操作")
                Toast.makeText(this, "静音操作失败：联系人数据为空", Toast.LENGTH_SHORT).show()
                return
            }
            
            Log.d(TAG, "开始切换联系人静音状态: ${contact.name} -> $isMuted")

            // 找到联系人所在的分类和位置
            var found = false
            for (categoryIndex in allContacts.indices) {
                val category = allContacts[categoryIndex]
                val contactIndex = category.contacts.indexOfFirst { it.id == contact.id }

                if (contactIndex != -1) {
                    val updatedContacts = category.contacts.toMutableList()
                    
                    // 安全地创建更新后的联系人对象
                    val updatedContact = try {
                        contact.copy(
                            id = contact.id ?: "",
                            name = contact.name ?: "未知联系人",
                            type = contact.type,
                            isMuted = isMuted,
                            aiMembers = contact.aiMembers ?: emptyList(),
                            customData = contact.customData ?: emptyMap()
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "创建更新联系人对象失败: ${e.message}")
                        Toast.makeText(this, "静音操作失败：数据更新异常", Toast.LENGTH_SHORT).show()
                        return
                    }
                    
                    updatedContacts[contactIndex] = updatedContact

                    allContacts[categoryIndex] = category.copy(contacts = updatedContacts.toMutableList())
                    found = true
                    break
                }
            }

            if (!found) {
                // 如果在allContacts中没找到，根据联系人类型添加到相应分类
                val targetCategory = if (contact.type == ContactType.AI) "AI助手" else "未分组"
                Log.d(TAG, "在allContacts中未找到联系人，尝试添加到$targetCategory")
                
                // 安全地创建更新后的联系人对象
                val updatedContact = try {
                    contact.copy(
                        id = contact.id ?: "",
                        name = contact.name ?: "未知联系人",
                        type = contact.type,
                        isMuted = isMuted,
                        aiMembers = contact.aiMembers ?: emptyList(),
                        customData = contact.customData ?: emptyMap()
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "创建新联系人对象失败: ${e.message}")
                    Toast.makeText(this, "静音操作失败：数据创建异常", Toast.LENGTH_SHORT).show()
                    return
                }

                // 查找或创建目标类别
                val categoryIndex = allContacts.indexOfFirst { it.name == targetCategory }
                if (categoryIndex != -1) {
                    val category = allContacts[categoryIndex]
                    val updatedContacts = category.contacts.toMutableList()
                    updatedContacts.add(updatedContact)
                    allContacts[categoryIndex] = category.copy(contacts = updatedContacts)
                } else {
                    // 创建新的类别
                    val newCategory = ContactCategory(
                        name = targetCategory,
                        contacts = mutableListOf(updatedContact),
                        isExpanded = true
                    )
                    if (targetCategory == "AI助手") {
                        allContacts.add(newCategory)
                    } else {
                        allContacts.add(0, newCategory)
                    }
                }
                found = true
            }

            if (found) {
                // 保存更新后的联系人数据
                saveContacts()

                // 立即刷新显示
                refreshCurrentTabDisplay()

                // 更新适配器
                chatContactAdapter?.updateContacts(allContacts)

                val action = if (isMuted) "已静音" else "已取消静音"
                Toast.makeText(this, "${contact.name} $action", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "切换联系人静音状态成功: ${contact.name} -> $isMuted")
            } else {
                Log.e(TAG, "无法找到联系人: ${contact.name}")
                Toast.makeText(this, "操作失败：找不到联系人", Toast.LENGTH_SHORT).show()
            }

        } catch (e: Exception) {
            Log.e(TAG, "切换联系人静音状态失败", e)
            Toast.makeText(this, "静音操作失败", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 切换分类展开状态
     */
    private fun toggleCategoryExpansion(category: ContactCategory) {
        try {
            val index = allContacts.indexOfFirst { it.name == category.name }
            if (index != -1) {
                allContacts[index] = category.copy(isExpanded = !category.isExpanded)

                // 保存更新后的联系人数据
                saveContacts()

                chatContactAdapter?.updateContacts(allContacts)
            }
            Log.d(TAG, "切换分类展开状态: ${category.name}")
        } catch (e: Exception) {
            Log.e(TAG, "切换分类展开状态失败", e)
        }
    }

    /**
     * 打开添加AI联系人对话框
     */
    private fun openAddAIContactDialog() {
        try {
            // 创建AI助手选择对话框
            val aiOptions = listOf(
                "DeepSeek" to "DeepSeek的AI助手",
                "ChatGPT" to "OpenAI的AI助手",
                "Claude" to "Anthropic的AI助手",
                "Gemini" to "Google的AI助手",
                "文心一言" to "百度的大语言模型",
                "通义千问" to "阿里巴巴的大语言模型",
                "讯飞星火" to "科大讯飞的AI助手",
                "Kimi" to "Moonshot的AI助手"
            )

            val aiNames = aiOptions.map { it.first }.toTypedArray()

            AlertDialog.Builder(this)
                .setTitle("选择AI助手")
                .setItems(aiNames) { _, which ->
                    val selectedAI = aiOptions[which]
                    showApiKeyInputDialog(selectedAI.first, selectedAI.second)
                }
                .setNegativeButton("取消", null)
                .show()

            Log.d(TAG, "打开AI助手选择对话框")
        } catch (e: Exception) {
            Log.e(TAG, "打开AI助手选择对话框失败", e)
            Toast.makeText(this, "打开AI助手选择对话框失败", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 显示API密钥输入对话框
     */
    private fun showApiKeyInputDialog(aiName: String, description: String) {
        try {
            // 创建API密钥输入对话框
            val input = EditText(this).apply {
                hint = "请输入${aiName}的API密钥"
                inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                setPadding(50, 30, 50, 30)
            }

            AlertDialog.Builder(this)
                .setTitle("配置${aiName}")
                .setMessage("请填写${aiName}的API密钥以激活对话功能")
                .setView(input)
                .setPositiveButton("确定") { _, _ ->
                    val apiKey = input.text.toString().trim()
                    if (apiKey.isNotEmpty()) {
                        // 验证API密钥格式
                        if (isValidApiKey(apiKey, aiName)) {
                            // 保存API密钥到设置
                            saveApiKeyForAI(aiName, apiKey)
                            // 添加AI助手到联系人列表
                            addAIContactToCategory(aiName, description, apiKey)
                            Toast.makeText(this, "${aiName}已激活，API密钥已保存", Toast.LENGTH_SHORT).show()
                        } else {
                            // 显示格式错误提示
                            val errorMsg = when (aiName.lowercase()) {
                                "deepseek", "chatgpt" -> "API密钥应该以'sk-'开头且长度至少20个字符"
                                "claude" -> "API密钥应该以'sk-ant-'开头且长度至少20个字符"
                                else -> "API密钥格式不正确，请检查"
                            }
                            Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Toast.makeText(this, "请输入API密钥", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("取消", null)
                .show()

            Log.d(TAG, "显示API密钥输入对话框: $aiName")
        } catch (e: Exception) {
            Log.e(TAG, "显示API密钥输入对话框失败", e)
            Toast.makeText(this, "显示API密钥输入对话框失败", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 验证API密钥是否有效
     */
    private fun isValidApiKey(apiKey: String, aiName: String): Boolean {
        if (apiKey.isBlank()) {
            return false
        }

        // 根据不同的AI服务验证API密钥格式
        return when (aiName.lowercase()) {
            "deepseek" -> apiKey.startsWith("sk-") && apiKey.length >= 20
            "chatgpt" -> apiKey.startsWith("sk-") && apiKey.length >= 20
            "claude" -> apiKey.startsWith("sk-ant-") && apiKey.length >= 20
            "gemini" -> apiKey.length >= 20 // Google API密钥没有固定前缀
            "智谱ai", "智谱AI" -> apiKey.contains(".") && apiKey.length >= 20 // 智谱AI API密钥格式：xxxxx.xxxxx
            "文心一言" -> apiKey.length >= 10 // 百度API密钥
            "通义千问" -> apiKey.length >= 10 // 阿里云API密钥
            "讯飞星火" -> apiKey.length >= 10 // 讯飞API密钥
            "kimi" -> apiKey.length >= 10 // Kimi API密钥
            else -> apiKey.length >= 10
        }
    }

    /**
     * 获取AI的API密钥
     */
    private fun getApiKeyForAI(aiName: String): String {
        try {
            val settingsManager = SettingsManager.getInstance(this)
            val keyName = when (aiName.lowercase()) {
                "deepseek" -> "deepseek_api_key"
                "chatgpt" -> "chatgpt_api_key"
                "claude" -> "claude_api_key"
                "gemini" -> "gemini_api_key"
                "智谱ai", "智谱AI" -> "zhipu_ai_api_key"
                "文心一言" -> "wenxin_api_key"
                "通义千问" -> "qianwen_api_key"
                "讯飞星火" -> "xinghuo_api_key"
                "kimi" -> "kimi_api_key"
                else -> "${aiName.lowercase()}_api_key"
            }
            return settingsManager.getString(keyName, "") ?: ""
        } catch (e: Exception) {
            Log.e(TAG, "获取API密钥失败", e)
            return ""
        }
    }

    /**
     * 保存AI的API密钥到设置
     */
    private fun saveApiKeyForAI(aiName: String, apiKey: String) {
        try {
            val settingsManager = SettingsManager.getInstance(this)
            val keyName = when (aiName.lowercase()) {
                "deepseek" -> "deepseek_api_key"
                "chatgpt" -> "chatgpt_api_key"
                "claude" -> "claude_api_key"
                "gemini" -> "gemini_api_key"
                "智谱ai", "智谱AI" -> "zhipu_ai_api_key"
                "文心一言" -> "wenxin_api_key"
                "通义千问" -> "qianwen_api_key"
                "讯飞星火" -> "xinghuo_api_key"
                "kimi" -> "kimi_api_key"
                else -> "${aiName.lowercase()}_api_key"
            }

            // 保存到SettingsManager（系统设置）
            settingsManager.putString(keyName, apiKey)

            // 同时保存API URL到设置中，确保系统设置完整
            val urlKeyName = keyName.replace("_api_key", "_api_url")
            val defaultUrl = getDefaultApiUrl(aiName)
            settingsManager.putString(urlKeyName, defaultUrl)

            Log.d(TAG, "保存API密钥到系统设置: $keyName")
            Log.d(TAG, "同步API URL到系统设置: $urlKeyName = $defaultUrl")

            // 通知其他组件API密钥已更新
            notifyApiKeyUpdated(aiName, apiKey)

        } catch (e: Exception) {
            Log.e(TAG, "保存API密钥失败", e)
        }
    }

    /**
     * 通知API密钥已更新
     */
    private fun notifyApiKeyUpdated(aiName: String, apiKey: String) {
        try {
            // 更新现有联系人的API密钥
            updateExistingContactApiKey(aiName, apiKey)

            // 发送广播通知其他组件（如果需要）
            val intent = Intent("com.example.aifloatingball.API_KEY_UPDATED").apply {
                putExtra("ai_name", aiName)
                putExtra("api_key", apiKey)
            }
            sendBroadcast(intent)

        } catch (e: Exception) {
            Log.e(TAG, "通知API密钥更新失败", e)
        }
    }

    /**
     * 更新现有联系人的API密钥
     */
    private fun updateExistingContactApiKey(aiName: String, newApiKey: String) {
        try {
            var updated = false

            for (category in allContacts) {
                for (contact in category.contacts) {
                    if (contact.name.equals(aiName, ignoreCase = true)) {
                        // 更新联系人的API密钥
                        val updatedCustomData = contact.customData.toMutableMap()
                        updatedCustomData["api_key"] = newApiKey
                        updatedCustomData["api_url"] = getDefaultApiUrl(aiName)

                        // 创建更新后的联系人对象（需要用反射或重新创建）
                        val updatedContact = contact.copy(customData = updatedCustomData)

                        // 在category中替换联系人
                        val contactIndex = category.contacts.indexOf(contact)
                        if (contactIndex >= 0) {
                            val updatedContacts = category.contacts.toMutableList()
                            updatedContacts[contactIndex] = updatedContact

                            val categoryIndex = allContacts.indexOf(category)
                            if (categoryIndex >= 0) {
                                val updatedCategory = category.copy(contacts = updatedContacts)
                                (allContacts as MutableList)[categoryIndex] = updatedCategory
                                updated = true
                            }
                        }
                        break
                    }
                }
                if (updated) break
            }

            if (updated) {
                // 保存更新后的联系人数据
                saveContacts()

                // 刷新UI
                chatContactAdapter?.updateContacts(allContacts)

                Log.d(TAG, "已更新现有联系人的API密钥: $aiName")
            }

        } catch (e: Exception) {
            Log.e(TAG, "更新现有联系人API密钥失败", e)
        }
    }

    /**
     * 添加AI助手到分类
     */
    private fun addAIContactToCategory(name: String, description: String, apiKey: String = "") {
        try {
            // 检查是否已存在该AI助手
            val existingContact = allContacts.flatMap { it.contacts }.find { it.name == name }
            if (existingContact != null) {
                Toast.makeText(this, "$name 已存在于联系人列表中", Toast.LENGTH_SHORT).show()
                return
            }

            // 创建新的AI联系人
            val newContact = ChatContact(
                id = "ai_${name.lowercase().replace(" ", "_")}_${System.currentTimeMillis()}",
                name = name,
                type = ContactType.AI,
                description = description,
                isOnline = true,
                lastMessage = "你好！我是$name，有什么可以帮助你的吗？",
                lastMessageTime = System.currentTimeMillis(),
                unreadCount = 0,
                isPinned = true, // 新添加的AI助手优先显示
                customData = mapOf(
                    "api_url" to getDefaultApiUrl(name),
                    "api_key" to apiKey,
                    "model" to getDefaultModel(name)
                ),
                aiMembers = emptyList()
            )

            // 添加到联系人列表
            addContactToList(newContact)

            // 标记AI为已配置
            markAIAsConfigured(name)

            Toast.makeText(this, "已添加 $name", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "添加AI助手到分类: $name")
        } catch (e: Exception) {
            Log.e(TAG, "添加AI助手到分类失败", e)
            Toast.makeText(this, "添加AI助手失败", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 获取默认API地址
     */
    private fun getDefaultApiUrl(aiName: String): String {
        return when (aiName.lowercase()) {
            "deepseek" -> "https://api.deepseek.com/v1/chat/completions"
            "chatgpt" -> "https://api.openai.com/v1/chat/completions"
            "claude" -> "https://api.anthropic.com/v1/messages"
            "gemini" -> "https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent"
            "文心一言" -> "https://aip.baidubce.com/rpc/2.0/ai_custom/v1/wenxinworkshop/chat/completions"
            "通义千问" -> "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation"
            "讯飞星火" -> "https://spark-api.xf-yun.com/v3.1/chat"
            "kimi" -> "https://api.moonshot.cn/v1/chat/completions"
            "智谱ai", "zhipu", "glm" -> "https://open.bigmodel.cn/api/paas/v4/chat/completions"
            else -> "https://api.openai.com/v1/chat/completions"
        }
    }

    /**
     * 获取默认模型名称
     */
    private fun getDefaultModel(aiName: String): String {
        return when (aiName.lowercase()) {
            "deepseek" -> "deepseek-chat"
            "chatgpt" -> "gpt-3.5-turbo"
            "claude" -> "claude-3-sonnet-20240229"
            "gemini" -> "gemini-pro"
            "文心一言" -> "ernie-bot-4"
            "通义千问" -> "qwen-turbo"
            "讯飞星火" -> "spark-v3.1"
            "kimi" -> "moonshot-v1-8k"
            "智谱ai", "zhipu", "glm" -> "glm-4"
            else -> "gpt-3.5-turbo"
        }
    }

    /**
     * 打开添加API联系人对话框
     */
    private fun openAddAPIContactDialog() {
        try {
            Log.d(TAG, "开始创建添加API联系人对话框")
            showActualAPIDialog()
        } catch (e: Exception) {
            Log.e(TAG, "打开添加API联系人对话框失败", e)
            Toast.makeText(this, "打开添加API联系人对话框失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * 显示实际的API配置对话框
     */
    private fun showActualAPIDialog() {
        try {
            Log.d(TAG, "开始创建实际的API配置对话框")

            // 检查布局文件是否存在
            val layoutId = R.layout.dialog_add_api_contact
            Log.d(TAG, "布局文件ID: $layoutId")

            val dialogView = LayoutInflater.from(this).inflate(layoutId, null)
            Log.d(TAG, "布局文件加载成功")

            val dialog = AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(true)
                .create()

            // 获取视图组件
            val aiModelSpinner = dialogView.findViewById<Spinner>(R.id.ai_model_spinner)
            val nameInput = dialogView.findViewById<EditText>(R.id.name_input)
            val apiUrlInput = dialogView.findViewById<EditText>(R.id.api_url_input)
            val modelInput = dialogView.findViewById<EditText>(R.id.model_input)
            val apiKeyInput = dialogView.findViewById<EditText>(R.id.api_key_input)
            val confirmButton = dialogView.findViewById<Button>(R.id.confirm_button)
            val cancelButton = dialogView.findViewById<Button>(R.id.cancel_button)

            // 检查所有视图是否成功找到
            if (aiModelSpinner == null || nameInput == null || apiUrlInput == null ||
                modelInput == null || apiKeyInput == null || confirmButton == null || cancelButton == null) {
                Log.e(TAG, "某些视图未找到")
                Toast.makeText(this, "对话框布局加载失败", Toast.LENGTH_SHORT).show()
                return
            }

            Log.d(TAG, "所有视图加载成功")

            // 定义AI模型选项
            val aiModels = listOf(
                "DeepSeek" to "DeepSeek的AI助手",
                "ChatGPT" to "OpenAI的AI助手",
                "Claude" to "Anthropic的AI助手",
                "Gemini" to "Google的AI助手",
                "智谱AI" to "智谱AI的GLM-4大语言模型",
                "文心一言" to "百度的大语言模型",
                "通义千问" to "阿里巴巴的大语言模型",
                "讯飞星火" to "科大讯飞的AI助手",
                "Kimi" to "Moonshot的AI助手"
            )

            val modelNames = aiModels.map { it.first }.toTypedArray()

            // 设置Spinner适配器 - 使用自定义布局支持暗色模式
            val spinnerAdapter = ArrayAdapter(this, R.layout.spinner_item, modelNames)
            spinnerAdapter.setDropDownViewResource(R.layout.dropdown_item)
            aiModelSpinner.adapter = spinnerAdapter

            // 设置Spinner选择监听器
            aiModelSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                    val selectedModel = aiModels[position]
                    val modelName = selectedModel.first

                    // 自动填充字段
                    nameInput.setText(modelName)
                    apiUrlInput.setText(getDefaultApiUrl(modelName))
                    modelInput.setText(getDefaultModel(modelName))

                    Log.d(TAG, "选择AI模型: $modelName")
                }

                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {
                    // 默认选择第一个
                    if (aiModels.isNotEmpty()) {
                        val defaultModel = aiModels[0]
                        nameInput.setText(defaultModel.first)
                        apiUrlInput.setText(getDefaultApiUrl(defaultModel.first))
                        modelInput.setText(getDefaultModel(defaultModel.first))
                    }
                }
            }

            // 设置确认按钮点击事件
            confirmButton.setOnClickListener {
                try {
                    val name = nameInput.text?.toString()?.trim() ?: ""
                    val apiUrl = apiUrlInput.text?.toString()?.trim() ?: ""
                    val model = modelInput.text?.toString()?.trim() ?: ""
                    val apiKey = apiKeyInput.text?.toString()?.trim() ?: ""

                    Log.d(TAG, "用户输入: name=$name, apiUrl=$apiUrl, model=$model, apiKey=${if (apiKey.isNotEmpty()) "已填写" else "未填写"}")

                    if (name.isNotEmpty() && apiUrl.isNotEmpty() && apiKey.isNotEmpty()) {
                        // 保存API密钥到SettingsManager
                        saveApiKeyForAI(name, apiKey)

                        // 创建新的AI联系人
                        val newContact = ChatContact(
                            id = "custom_ai_${System.currentTimeMillis()}",
                            name = name,
                            type = ContactType.AI,
                            description = "自定义AI助手",
                            isOnline = true,
                            lastMessage = "你好！我是$name，有什么可以帮助你的吗？",
                            lastMessageTime = System.currentTimeMillis(),
                            unreadCount = 0,
                            isPinned = true, // 自定义API的AI优先显示
                            customData = mapOf(
                                "api_url" to apiUrl,
                                "api_key" to apiKey,
                                "model" to model.ifEmpty { "default" }
                            ),
                            aiMembers = emptyList()
                        )

                        // 添加到联系人列表
                        addContactToList(newContact)
                        dialog.dismiss()
                        Toast.makeText(this, "AI助手 $name 添加成功，API密钥已保存", Toast.LENGTH_SHORT).show()
                        Log.d(TAG, "添加自定义API AI联系人成功: $name")
                    } else {
                        val errorMsg = "请填写API密钥"
                        Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show()
                        Log.d(TAG, "用户输入不完整: $errorMsg")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "处理确认按钮点击事件失败", e)
                    Toast.makeText(this, "处理失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            // 设置取消按钮点击事件
            cancelButton.setOnClickListener {
                dialog.dismiss()
                Log.d(TAG, "用户取消添加API联系人")
            }

            // 显示对话框
            dialog.show()
            Log.d(TAG, "添加API联系人对话框显示成功")

        } catch (e: Exception) {
            Log.e(TAG, "显示实际API配置对话框失败", e)
            Toast.makeText(this, "显示API配置对话框失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }





    /**
     * 删除联系人（内部方法，不再显示确认对话框）
     */
    private fun deleteContact(contact: ChatContact) {
        try {
            when (contact.type) {
                ContactType.AI -> {
                    removeAIConfiguration(contact)
                }
                ContactType.GROUP -> {
                    removeGroupChatConfiguration(contact)
                }
                else -> {
                    removeContactFromList(contact)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "删除联系人失败", e)
            Toast.makeText(this, "删除操作失败", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 移除AI配置
     */
    private fun removeAIConfiguration(contact: ChatContact) {
        try {
            // 获取当前分组
            val currentGroup = findContactGroup(contact)

            // 清除API密钥
            val prefs = getSharedPreferences("ai_api_keys", MODE_PRIVATE)
            prefs.edit().remove(contact.name).apply()

            // 从所有分组中移除该AI
            val groupsToCheck = mutableListOf<String>()
            for (categoryIndex in allContacts.indices) {
                val category = allContacts[categoryIndex]
                val updatedContacts = category.contacts.filter { it.id != contact.id }.toMutableList().toMutableList()
                if (updatedContacts.size != category.contacts.size) {
                    allContacts[categoryIndex] = category.copy(contacts = updatedContacts)

                    // 检查是否需要移除空分组标签页
                    if (updatedContacts.isEmpty() &&
                        category.name != "AI助手" &&
                        category.name != "全部" &&
                        category.name != "未分组") {
                        groupsToCheck.add(category.name)
                    }
                }
            }

            // 移除空分组的标签页
            groupsToCheck.forEach { groupName ->
                removeEmptyGroupTab(groupName)
            }

            // 保存更改
            saveContacts()

            // 刷新显示（不切换到"全部"标签页，避免弹窗）
            refreshCurrentTabDisplay()

            // 更新适配器
            chatContactAdapter?.updateContacts(allContacts)

            Log.d(TAG, "移除AI配置成功: ${contact.name}")

        } catch (e: Exception) {
            Log.e(TAG, "移除AI配置失败", e)
            Toast.makeText(this, "❌ 移除配置失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 移除群聊配置
     */
    private fun removeGroupChatConfiguration(contact: ChatContact) {
        try {
            Log.d(TAG, "=== 开始删除群聊: ${contact.name} (ID: ${contact.id}, GroupID: ${contact.groupId}) ===")
            
            // 从所有数据源删除群聊数据
            if (contact.groupId != null) {
                // 1. 从UnifiedGroupChatManager删除
                Log.d(TAG, "删除前UnifiedGroupChatManager中的群聊数量: ${unifiedGroupChatManager.getAllGroupChats().size}")
                val deleted = unifiedGroupChatManager.deleteGroupChat(contact.groupId!!)
                Log.d(TAG, "从UnifiedGroupChatManager删除群聊: ${contact.groupId}, 结果: $deleted")
                Log.d(TAG, "删除后UnifiedGroupChatManager中的群聊数量: ${unifiedGroupChatManager.getAllGroupChats().size}")
                
                // 2. 从GroupChatManager删除（如果存在）
                try {
                    val groupChatManager = GroupChatManager.getInstance(this)
                    val groupChatManagerDeleted = groupChatManager.deleteGroupChat(contact.groupId!!)
                    Log.d(TAG, "从GroupChatManager删除群聊: ${contact.groupId}, 结果: $groupChatManagerDeleted")
                } catch (e: Exception) {
                    Log.w(TAG, "从GroupChatManager删除群聊失败: ${e.message}")
                }
            } else {
                Log.w(TAG, "群聊联系人缺少groupId，无法从管理器删除")
            }

            // 从所有分组中移除该群聊联系人
            val groupsToCheck = mutableListOf<String>()
            for (categoryIndex in allContacts.indices) {
                val category = allContacts[categoryIndex]
                val updatedContacts = category.contacts.filter { it.id != contact.id }.toMutableList()
                if (updatedContacts.size != category.contacts.size) {
                    allContacts[categoryIndex] = category.copy(contacts = updatedContacts)

                    // 检查是否需要移除空分组标签页
                    if (updatedContacts.isEmpty() &&
                        category.name != "AI助手" &&
                        category.name != "全部" &&
                        category.name != "未分组") {
                        groupsToCheck.add(category.name)
                    }
                }
            }

            // 移除空分组的标签页
            groupsToCheck.forEach { groupName ->
                removeEmptyGroupTab(groupName)
            }

            // 保存更改
            saveContacts()

            // 刷新显示（不切换到"全部"标签页，避免弹窗）
            refreshCurrentTabDisplay()

            // 更新适配器
            chatContactAdapter?.updateContacts(allContacts)

            Log.d(TAG, "移除群聊配置成功: ${contact.name}")
            Log.d(TAG, "删除完成后UnifiedGroupChatManager中的群聊数量: ${unifiedGroupChatManager.getAllGroupChats().size}")
            Log.d(TAG, "=== 群聊删除完成 ===")
            Toast.makeText(this, "✅ 群聊 ${contact.name} 已删除", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Log.e(TAG, "移除群聊配置失败", e)
            Toast.makeText(this, "❌ 删除群聊失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 从联系人列表中删除联系人
     */
    private fun removeContactFromList(contact: ChatContact) {
        try {
            // 找到联系人所在的分类
            val categoryIndex = allContacts.indexOfFirst { category ->
                category.contacts.any { it.id == contact.id }
            }

            if (categoryIndex != -1) {
                val category = allContacts[categoryIndex]
                val updatedContacts = category.contacts.filter { it.id != contact.id }

                if (updatedContacts.isEmpty()) {
                    // 如果分类中没有联系人了，检查是否需要删除分组标签页
                    if (category.name != "AI助手" &&
                        category.name != "全部" &&
                        category.name != "未分组") {
                        removeEmptyGroupTab(category.name)
                    }
                    // 删除整个分类
                    allContacts.removeAt(categoryIndex)
                } else {
                    // 更新分类中的联系人列表
                    allContacts[categoryIndex] = category.copy(contacts = updatedContacts.toMutableList())
                }

                // 保存更新后的联系人数据
                saveContacts()

                // 刷新显示（不切换到"全部"标签页，避免弹窗）
                refreshCurrentTabDisplay()

                // 更新适配器
                chatContactAdapter?.updateContacts(allContacts)
            }
        } catch (e: Exception) {
            Log.e(TAG, "从列表中删除联系人失败", e)
        }
    }

    /**
     * 处理Activity结果
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            REQUEST_CODE_ADD_AI_CONTACT -> {
                // 从AI联系人列表界面返回
                if (resultCode == Activity.RESULT_OK && data != null) {
                    val aiContact = data.getParcelableExtra<ChatContact>("extra_ai_contact")
                    if (aiContact != null) {
                        // 添加AI联系人到对话列表
                        addAIContactToCategory(aiContact.name, aiContact.description ?: "AI助手")
                        // 刷新联系人列表显示
                        refreshChatContactsList()
                        Toast.makeText(this, "已添加 ${aiContact.name} 到对话列表", Toast.LENGTH_SHORT).show()
                        Log.d(TAG, "成功添加AI联系人: ${aiContact.name}")
                    } else {
                        Toast.makeText(this, "AI联系人数据无效", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Log.d(TAG, "用户取消了AI联系人选择")
                }
            }
        }
    }

    /**
     * 刷新对话联系人列表
     */
    private fun refreshChatContactsList() {
        try {
            // 重新从存储中加载所有联系人数据（包括AI和群聊）
            val savedContacts = loadSavedContacts()
            if (savedContacts.isNotEmpty()) {
                allContacts = savedContacts.toMutableList()
                Log.d(TAG, "从存储中重新加载了 ${savedContacts.size} 个联系人分类")
            }
            
            // 重新加载群聊数据并添加到联系人列表
            try {
                val groupChats = unifiedGroupChatManager.getAllGroupChats()
                if (groupChats.isNotEmpty()) {
                    // 将GroupChat转换为ChatContact
                    val groupChatContacts = groupChats.map { groupChat ->
                        ChatContact(
                            id = groupChat.id,
                            name = groupChat.name,
                            avatar = groupChat.avatar,
                            type = ContactType.GROUP,
                            description = groupChat.description,
                            isOnline = true,
                            lastMessage = groupChat.lastMessage,
                            lastMessageTime = groupChat.lastMessageTime,
                            unreadCount = groupChat.unreadCount,
                            isPinned = groupChat.isPinned,
                            isMuted = groupChat.isMuted,
                            groupId = groupChat.id,
                            memberCount = groupChat.members.size,
                            aiMembers = groupChat.members.filter { it.type == MemberType.AI }.map { it.name }
                        )
                    }
                    
                    // 清除现有的群聊联系人，避免重复
                    for (i in allContacts.indices) {
                        val category = allContacts[i]
                        val updatedContacts = category.contacts.filter { it.type != ContactType.GROUP }.toMutableList()
                        allContacts[i] = category.copy(contacts = updatedContacts)
                    }
                    
                    // 确保有"全部"分类来存放群聊
                    val allCategoryIndex = allContacts.indexOfFirst { it.name == "全部" }
                    if (allCategoryIndex != -1) {
                        val allCategory = allContacts[allCategoryIndex]
                        val updatedContacts = allCategory.contacts.toMutableList()
                        updatedContacts.addAll(groupChatContacts)
                        
                        // 重新排序
                        val sortedContacts = updatedContacts.sortedWith(
                            compareByDescending<ChatContact> { it.isPinned }
                                .thenByDescending { it.lastMessageTime }
                        ).toMutableList()
                        
                        allContacts[allCategoryIndex] = allCategory.copy(contacts = sortedContacts)
                    } else {
                        // 如果"全部"分类不存在，创建新分类
                        allContacts.add(0, ContactCategory("全部", groupChatContacts.toMutableList()))
                    }
                    Log.d(TAG, "重新加载了 ${groupChats.size} 个群聊到'全部'分类中")
                }
            } catch (e: Exception) {
                Log.e(TAG, "重新加载群聊数据失败", e)
            }
            
            // 显示所有联系人（AI和群聊）
            showAllUserAIContacts()
            
            // 通知适配器数据已更改
            chatContactAdapter?.updateContacts(allContacts)
            
            Log.d(TAG, "已刷新对话联系人列表")
        } catch (e: Exception) {
            Log.e(TAG, "刷新对话联系人列表失败", e)
        }
    }

    /**
     * 保存联系人数据到SharedPreferences
     */
    private fun saveContacts() {
        try {
            val prefs = getSharedPreferences(CONTACTS_PREFS_NAME, MODE_PRIVATE)
            val gson = Gson()
            val json = gson.toJson(allContacts)
            prefs.edit().putString(KEY_SAVED_CONTACTS, json).apply()
            Log.d(TAG, "联系人数据已保存")
        } catch (e: Exception) {
            Log.e(TAG, "保存联系人数据失败", e)
        }
    }

    /**
     * 从SharedPreferences加载联系人数据
     */
    private fun loadSavedContacts(): List<ContactCategory> {
        return try {
            val prefs = getSharedPreferences(CONTACTS_PREFS_NAME, MODE_PRIVATE)
            val json = prefs.getString(KEY_SAVED_CONTACTS, null)
            if (json != null) {
                val gson = Gson()
                val type = object : TypeToken<List<ContactCategory>>() {}.type
                gson.fromJson(json, type) ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "加载保存的联系人数据失败", e)
            emptyList()
        }
    }

    /**
     * 添加联系人到列表
     */
    private fun addContactToList(contact: ChatContact) {
        try {
            val categoryName = when (contact.type) {
                ContactType.AI -> "AI助手"
                ContactType.GROUP -> "全部"  // 群聊联系人添加到"全部"分组
            }

            // 查找或创建对应的分类
            val categoryIndex = allContacts.indexOfFirst { it.name == categoryName }

            if (categoryIndex != -1) {
                // 分类已存在，添加联系人到现有分类
                val category = allContacts[categoryIndex]
                val updatedContacts = category.contacts.toMutableList()

                // 检查是否已存在相同ID的联系人
                val existingIndex = updatedContacts.indexOfFirst { it.id == contact.id }
                if (existingIndex != -1) {
                    // 如果已存在，更新联系人信息
                    updatedContacts[existingIndex] = contact
                } else {
                    // 如果不存在，添加新联系人
                    updatedContacts.add(contact)
                }

                // 重新排序：置顶的联系人在前面，然后按最后消息时间排序
                val sortedContacts = updatedContacts.sortedWith(
                    compareByDescending<ChatContact> { it.isPinned }
                        .thenByDescending { it.lastMessageTime }
                ).toMutableList()

                allContacts[categoryIndex] = category.copy(contacts = sortedContacts)
            } else {
                // 分类不存在，创建新分类
                val newCategory = ContactCategory(categoryName, mutableListOf(contact))
                allContacts.add(newCategory)
            }

            // 保存更新后的联系人数据
            saveContacts()

            // 更新适配器
            chatContactAdapter?.updateContacts(allContacts)

            Toast.makeText(this, "已添加 ${contact.name}", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "添加联系人到列表: ${contact.name}")
        } catch (e: Exception) {
            Log.e(TAG, "添加联系人到列表失败", e)
        }
    }

    /**
     * 设置添加AI联系人广播接收器
     */
    private fun setupAddAIContactReceiver() {
        try {
            addAIContactReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    try {
                        when (intent?.action) {
                            "com.example.aifloatingball.ADD_AI_CONTACT" -> {
                                val aiName = intent.getStringExtra("ai_name") ?: return
                                val aiDisplayName = intent.getStringExtra("ai_display_name") ?: aiName
                                val aiDescription = intent.getStringExtra("ai_description") ?: ""

                                // 添加AI联系人到列表
                                addAIContactToCategory(aiName, aiDisplayName, getApiKeyForAI(aiName))

                                // 标记AI为已配置
                                markAIAsConfigured(aiName)

                                // 刷新UI
                                chatContactAdapter?.updateContacts(allContacts)

                                Toast.makeText(context, "✅ ${aiDisplayName} 已添加到对话列表", Toast.LENGTH_SHORT).show()
                            }
                            "com.example.aifloatingball.OPEN_AI_CHAT" -> {
                                val aiName = intent.getStringExtra("ai_name") ?: return

                                // 找到对应的AI联系人并打开对话
                                val aiContact = findAIContactByName(aiName)
                                if (aiContact != null) {
                                    val chatIntent = Intent(this@SimpleModeActivity, ChatActivity::class.java).apply {
                                        putExtra(ChatActivity.EXTRA_CONTACT, aiContact)
                                    }
                                    startActivity(chatIntent)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "处理添加AI联系人广播失败", e)
                    }
                }
            }

            val filter = IntentFilter().apply {
                addAction("com.example.aifloatingball.ADD_AI_CONTACT")
                addAction("com.example.aifloatingball.OPEN_AI_CHAT")
            }
            registerReceiver(addAIContactReceiver, filter)

        } catch (e: Exception) {
            Log.e(TAG, "设置添加AI联系人接收器失败", e)
        }
    }

    /**
     * 根据名称查找AI联系人
     */
    private fun findAIContactByName(aiName: String): ChatContact? {
        return try {
            allContacts.forEach { category ->
                category.contacts.forEach { contact ->
                    if (contact.name.equals(aiName, ignoreCase = true) ||
                        contact.name.contains(aiName, ignoreCase = true)) {
                        return contact
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "查找AI联系人失败", e)
            null
        }
    }

    /**
     * 标记AI为已配置
     */
    private fun markAIAsConfigured(aiName: String) {
        try {
            val configuredAIs = settingsManager.getString("configured_ais", "") ?: ""
            val aiList = configuredAIs.split(",").toMutableSet()
            aiList.add(aiName)
            settingsManager.putString("configured_ais", aiList.filter { it.isNotBlank() }.joinToString(","))
        } catch (e: Exception) {
            Log.e(TAG, "标记AI为已配置失败", e)
        }
    }

    /**
     * 移除AI配置标记
     */
    private fun unmarkAIAsConfigured(aiName: String) {
        try {
            val configuredAIs = settingsManager.getString("configured_ais", "") ?: ""
            val aiList = configuredAIs.split(",").toMutableSet()
            aiList.remove(aiName)
            settingsManager.putString("configured_ais", aiList.filter { it.isNotBlank() }.joinToString(","))
        } catch (e: Exception) {
            Log.e(TAG, "移除AI配置标记失败", e)
        }
    }

    /**
     * 根据联系人信息获取AI服务类型
     */
    private fun getAIServiceType(contact: ChatContact): AIServiceType? {
        return when (contact.name.lowercase()) {
            "chatgpt", "gpt" -> AIServiceType.CHATGPT
            "claude" -> AIServiceType.CLAUDE
            "gemini" -> AIServiceType.GEMINI
            "文心一言", "wenxin" -> AIServiceType.WENXIN
            "deepseek" -> AIServiceType.DEEPSEEK
            "通义千问", "qianwen" -> AIServiceType.QIANWEN
            "讯飞星火", "xinghuo" -> AIServiceType.XINGHUO
            "kimi" -> AIServiceType.KIMI
            "智谱ai", "智谱清言", "zhipu", "glm" -> AIServiceType.ZHIPU_AI
            else -> null
        }
    }

    /**
     * 获取指定服务的API密钥
     */
    private fun getApiKeyForService(serviceType: AIServiceType): String {
        val settingsManager = SettingsManager.getInstance(this)
        return when (serviceType) {
            AIServiceType.CHATGPT -> settingsManager.getString("chatgpt_api_key", "") ?: ""
            AIServiceType.CLAUDE -> settingsManager.getString("claude_api_key", "") ?: ""
            AIServiceType.GEMINI -> settingsManager.getGeminiApiKey()
            AIServiceType.WENXIN -> settingsManager.getWenxinApiKey()
            AIServiceType.DEEPSEEK -> settingsManager.getDeepSeekApiKey()
            AIServiceType.QIANWEN -> settingsManager.getQianwenApiKey()
            AIServiceType.XINGHUO -> settingsManager.getString("xinghuo_api_key", "") ?: ""
            AIServiceType.KIMI -> settingsManager.getKimiApiKey()
            AIServiceType.ZHIPU_AI -> settingsManager.getString("zhipu_ai_api_key", "") ?: ""
        }
    }

    /**
     * 发送消息到AI服务（异步版本）
     */
    private suspend fun sendMessageToAI(
        serviceType: AIServiceType,
        message: String,
        conversationHistory: List<Map<String, String>>,
        apiKey: String
    ): String {
        return withContext(Dispatchers.IO) {
            try {
                // 创建AI API管理器
                val aiApiManager = AIApiManager(this@SimpleModeActivity)

                // 使用协程和CompletableDeferred来异步等待结果
                val deferred = CompletableDeferred<String>()

                aiApiManager.sendMessage(
                    serviceType = serviceType,
                    message = message,
                    conversationHistory = conversationHistory,
                    callback = object : AIApiManager.StreamingCallback {
                        override fun onChunkReceived(chunk: String) {
                            // 流式响应处理
                        }

                        override fun onComplete(fullResponse: String) {
                            deferred.complete(fullResponse)
                        }

                        override fun onError(error: String) {
                            deferred.complete("抱歉，发生了错误：$error")
                        }
                    }
                )

                // 使用withTimeout来设置超时，避免无限等待
                try {
                    withTimeout(10000) { // 10秒超时
                        deferred.await()
                    }
                } catch (e: TimeoutCancellationException) {
                    "抱歉，请求超时，请稍后重试"
                }

            } catch (e: Exception) {
                Log.e(TAG, "发送消息到AI失败", e)
                "抱歉，发生了错误：${e.message}"
            }
        }
    }

    /**
     * 显示Material Design风格的自定义标签页对话框
     */
    private fun showCustomTabDialog() {
        try {
            // 创建对话框布局
            val dialogView = layoutInflater.inflate(R.layout.dialog_custom_tab_material, null)
            val inputLayout = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.tab_name_input_layout)
            val input = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.tab_name_input)
            val chipGroup = dialogView.findViewById<com.google.android.material.chip.ChipGroup>(R.id.suggested_tags_group)
            val btnCancel = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_cancel)
            val btnCreate = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_create)

            // 设置默认值
            input.setText("AI分组")
            input.selectAll()

            // 设置建议标签点击事件
            val chipWork = dialogView.findViewById<com.google.android.material.chip.Chip>(R.id.chip_work)
            val chipStudy = dialogView.findViewById<com.google.android.material.chip.Chip>(R.id.chip_study)
            val chipCreative = dialogView.findViewById<com.google.android.material.chip.Chip>(R.id.chip_creative)
            val chipLife = dialogView.findViewById<com.google.android.material.chip.Chip>(R.id.chip_life)

            chipWork.setOnClickListener { input.setText("工作助手") }
            chipStudy.setOnClickListener { input.setText("学习助手") }
            chipCreative.setOnClickListener { input.setText("创作助手") }
            chipLife.setOnClickListener { input.setText("生活助手") }

            // 输入验证
            input.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    val text = s?.toString()?.trim() ?: ""
                    when {
                        text.isEmpty() -> {
                            inputLayout.error = "请输入标签页名称"
                            btnCreate.isEnabled = false
                        }
                        text.length > 10 -> {
                            inputLayout.error = "标签页名称不能超过10个字符"
                            btnCreate.isEnabled = false
                        }
                        else -> {
                            inputLayout.error = null
                            btnCreate.isEnabled = true
                        }
                    }
                }
            })

            // 创建对话框
            val dialog = androidx.appcompat.app.AlertDialog.Builder(this, R.style.Theme_MaterialDialog)
                .setView(dialogView)
                .setCancelable(true)
                .create()

            // 设置按钮事件
            btnCancel.setOnClickListener { dialog.dismiss() }

            btnCreate.setOnClickListener {
                val tabName = input.text.toString().trim()
                if (tabName.isNotEmpty() && tabName.length <= 10) {
                    createCustomTab(tabName)
                    dialog.dismiss()
                }
            }

            dialog.show()

            // 自动弹出键盘
            input.requestFocus()
            val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(input, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)

        } catch (e: Exception) {
            Log.e(TAG, "显示自定义标签页对话框失败", e)
        }
    }



    /**
     * 显示编辑自定义标签页对话框
     */
    private fun showEditCustomTabDialog(tab: com.google.android.material.tabs.TabLayout.Tab) {
        try {
            val currentName = tab.text.toString()
            val input = EditText(this).apply {
                hint = "输入新的标签页名称"
                setText(currentName)
            }

            AlertDialog.Builder(this)
                .setTitle("编辑标签页")
                .setView(input)
                .setPositiveButton("保存") { _, _ ->
                    val newName = input.text.toString().trim()
                    if (newName.isNotEmpty() && newName != currentName) {
                        editCustomTab(tab, newName)
                    }
                }
                .setNegativeButton("删除") { _, _ ->
                    deleteCustomTab(tab)
                }
                .setNeutralButton("取消", null)
                .show()
        } catch (e: Exception) {
            Log.e(TAG, "显示编辑自定义标签页对话框失败", e)
        }
    }

    /**
     * 编辑自定义标签页
     */
    private fun editCustomTab(tab: com.google.android.material.tabs.TabLayout.Tab, newName: String) {
        try {
            tab.text = newName
            updateCustomTabName(tab.position, newName)
            Toast.makeText(this, "标签页已重命名", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "编辑自定义标签页失败", e)
        }
    }

    /**
     * 删除自定义标签页
     */
    private fun deleteCustomTab(tab: com.google.android.material.tabs.TabLayout.Tab) {
        try {
            val chatTabLayout = findViewById<com.google.android.material.tabs.TabLayout>(R.id.chat_tab_layout)
            chatTabLayout?.removeTab(tab)
            removeCustomTab(tab.position)
            Toast.makeText(this, "标签页已删除", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "删除自定义标签页失败", e)
        }
    }

    /**
     * 显示自定义标签页中的AI联系人
     */
    private fun showCustomAIContacts(tabName: String) {
        try {
            // 获取该标签页中配置的AI联系人
            val customAIContacts = getCustomTabAIContacts(tabName)
            chatContactAdapter?.updateContacts(customAIContacts)
            Log.d(TAG, "显示自定义标签页 $tabName 中的AI联系人，数量: ${customAIContacts.size}")
        } catch (e: Exception) {
            Log.e(TAG, "显示自定义标签页AI联系人失败", e)
        }
    }

    /**
     * 保存自定义标签页信息
     */
    private fun saveCustomTab(tabName: String) {
        try {
            val settingsManager = SettingsManager.getInstance(this)
            val customTabs = settingsManager.getString("custom_ai_tabs", "") ?: ""
            val tabList = if (customTabs.isNotEmpty()) {
                customTabs.split(",").toMutableList()
            } else {
                mutableListOf()
            }

            if (!tabList.contains(tabName)) {
                tabList.add(tabName)
                settingsManager.putString("custom_ai_tabs", tabList.joinToString(","))
            }
        } catch (e: Exception) {
            Log.e(TAG, "保存自定义标签页失败", e)
        }
    }

    /**
     * 更新自定义标签页名称
     */
    private fun updateCustomTabName(tabPosition: Int, newName: String) {
        try {
            val settingsManager = SettingsManager.getInstance(this)
            val customTabs = settingsManager.getString("custom_ai_tabs", "") ?: ""
            val tabList = if (customTabs.isNotEmpty()) {
                customTabs.split(",").toMutableList()
            } else {
                mutableListOf()
            }

            if (tabPosition - 3 < tabList.size) {
                tabList[tabPosition - 3] = newName
                settingsManager.putString("custom_ai_tabs", tabList.joinToString(","))
            }
        } catch (e: Exception) {
            Log.e(TAG, "更新自定义标签页名称失败", e)
        }
    }

    /**
     * 移除自定义标签页
     */
    private fun removeCustomTab(tabPosition: Int) {
        try {
            val settingsManager = SettingsManager.getInstance(this)
            val customTabs = settingsManager.getString("custom_ai_tabs", "") ?: ""
            val tabList = if (customTabs.isNotEmpty()) {
                customTabs.split(",").toMutableList()
            } else {
                mutableListOf()
            }

            if (tabPosition - 3 < tabList.size) {
                tabList.removeAt(tabPosition - 3)
                settingsManager.putString("custom_ai_tabs", tabList.joinToString(","))
            }
        } catch (e: Exception) {
            Log.e(TAG, "移除自定义标签页失败", e)
        }
    }

    /**
     * 获取自定义标签页中的AI联系人
     */
    private fun getCustomTabAIContacts(tabName: String): List<ContactCategory> {
        try {
            val settingsManager = SettingsManager.getInstance(this)
            val tabAIConfig = settingsManager.getString("custom_tab_ai_${tabName}", "") ?: ""

            if (tabAIConfig.isNotEmpty()) {
                val aiIds = tabAIConfig.split(",")
                val aiContacts = allContacts.flatMap { it.contacts }.filter { contact ->
                    aiIds.contains(contact.id) && isAIContact(contact)
                }.toMutableList()
                // 创建一个包含AI联系人的ContactCategory
                return listOf(ContactCategory(
                    name = tabName,
                    contacts = aiContacts,
                    isExpanded = true
                ))
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取自定义标签页AI联系人失败", e)
        }

        return emptyList()
    }



    /**
     * 将联系人添加到当前标签页
     */
    private fun addContactToCurrentTab(contact: ChatContact) {
        try {
            val chatTabLayout = findViewById<com.google.android.material.tabs.TabLayout>(R.id.chat_tab_layout)
            val currentTabPosition = chatTabLayout?.selectedTabPosition ?: 0

            when (currentTabPosition) {
                0, 1 -> {
                    // "全部"和"AI助手"标签页不需要添加
                    Toast.makeText(this, "此标签页不支持添加AI对象", Toast.LENGTH_SHORT).show()
                }
                2 -> {
                    // "+"标签页，显示选择标签页对话框
                    showSelectTabForContactDialog(contact)
                }
                else -> {
                    // 自定义标签页，直接添加
                    val customTabName = chatTabLayout?.getTabAt(currentTabPosition)?.text?.toString()
                    if (customTabName != null && customTabName != "+") {
                        addContactToCustomTab(contact, customTabName)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "添加联系人到当前标签页失败", e)
        }
    }

    /**
     * 显示选择标签页对话框
     */
    private fun showSelectTabForContactDialog(contact: ChatContact) {
        try {
            val settingsManager = SettingsManager.getInstance(this)
            val customTabs = settingsManager.getString("custom_ai_tabs", "") ?: ""

            if (customTabs.isEmpty()) {
                Toast.makeText(this, "请先创建自定义标签页", Toast.LENGTH_SHORT).show()
                return
            }

            val tabNames = customTabs.split(",")
            val items = tabNames.toTypedArray()

            AlertDialog.Builder(this)
                .setTitle("选择要添加到的标签页")
                .setItems(items) { _, which ->
                    val selectedTabName = items[which]
                    addContactToCustomTab(contact, selectedTabName)
                }
                .setNegativeButton("取消", null)
                .show()
        } catch (e: Exception) {
            Log.e(TAG, "显示选择标签页对话框失败", e)
        }
    }

    /**
     * 将联系人添加到自定义标签页
     */
    private fun addContactToCustomTab(contact: ChatContact, tabName: String) {
        try {
            if (!isAIContact(contact)) {
                Toast.makeText(this, "只能添加AI助手到自定义标签页", Toast.LENGTH_SHORT).show()
                return
            }

            val settingsManager = SettingsManager.getInstance(this)
            val tabAIConfig = settingsManager.getString("custom_tab_ai_${tabName}", "") ?: ""
            val aiIds = if (tabAIConfig.isNotEmpty()) {
                tabAIConfig.split(",").toMutableList()
            } else {
                mutableListOf()
            }

            if (!aiIds.contains(contact.id)) {
                aiIds.add(contact.id)
                settingsManager.putString("custom_tab_ai_${tabName}", aiIds.joinToString(","))
                Toast.makeText(this, "已将 ${contact.name} 添加到标签页 \"$tabName\"", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "${contact.name} 已在标签页 \"$tabName\" 中", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "添加联系人到自定义标签页失败", e)
        }
    }

    /**
     * 从自定义标签页移除联系人
     */
    private fun removeContactFromCustomTab(contact: ChatContact, tabName: String) {
        try {
            val settingsManager = SettingsManager.getInstance(this)
            val tabAIConfig = settingsManager.getString("custom_tab_ai_${tabName}", "") ?: ""
            val aiIds = if (tabAIConfig.isNotEmpty()) {
                tabAIConfig.split(",").toMutableList()
            } else {
                mutableListOf()
            }

            if (aiIds.contains(contact.id)) {
                aiIds.remove(contact.id)
                settingsManager.putString("custom_tab_ai_${tabName}", aiIds.joinToString(","))
                Toast.makeText(this, "已从标签页 \"$tabName\" 移除 ${contact.name}", Toast.LENGTH_SHORT).show()

                // 如果当前正在显示该标签页，刷新显示
                val chatTabLayout = findViewById<com.google.android.material.tabs.TabLayout>(R.id.chat_tab_layout)
                val currentTabPosition = chatTabLayout?.selectedTabPosition ?: 0
                if (currentTabPosition > 2) {
                    val currentTabName = chatTabLayout?.getTabAt(currentTabPosition)?.text?.toString()
                    if (currentTabName == tabName) {
                        showCustomAIContacts(tabName)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "从自定义标签页移除联系人失败", e)
        }
    }

    /**
     * 创建自定义标签页
     */
    private fun createCustomTab(tabName: String) {
        try {
            val prefs = getSharedPreferences("custom_tabs", MODE_PRIVATE)
            val existingTabs = prefs.getString("custom_ai_tabs", "") ?: ""

            val tabList = if (existingTabs.isNotEmpty()) {
                existingTabs.split(",").toMutableList()
            } else {
                mutableListOf()
            }

            if (!tabList.contains(tabName)) {
                tabList.add(tabName)
                prefs.edit().putString("custom_ai_tabs", tabList.joinToString(",")).apply()

                // 标签页已创建
                Log.d(TAG, "自定义标签页已保存")

                Log.d(TAG, "创建自定义标签页: $tabName")
            } else {
                Toast.makeText(this, "标签页 \"$tabName\" 已存在", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "创建自定义标签页失败", e)
            Toast.makeText(this, "创建标签页失败", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 创建自定义分组
     */
    private fun createCustomGroup(groupName: String) {
        try {
            // 检查分组是否已存在
            val existingCategory = allContacts.find { it.name == groupName }
            if (existingCategory != null) {
                Toast.makeText(this, "分组 \"$groupName\" 已存在", Toast.LENGTH_SHORT).show()
                return
            }

            // 如果创建的是AI助手分组，清除删除标记
            if (groupName == "AI助手") {
                val prefs = getSharedPreferences("custom_tabs", MODE_PRIVATE)
                prefs.edit().putBoolean("ai_assistant_group_deleted", false).apply()
                Log.d(TAG, "重新创建AI助手分组，清除删除标记")
            }

            // 创建新的分组
            val newCategory = ContactCategory(
                name = groupName,
                contacts = mutableListOf(),
                isExpanded = true
            )
            allContacts.add(newCategory)

            // 保存到SharedPreferences
            saveContacts()

            // 在TabLayout中添加新标签
            val chatTabLayout = findViewById<com.google.android.material.tabs.TabLayout>(R.id.chat_tab_layout)
            val newTab = chatTabLayout?.newTab()?.setText(groupName)
            if (newTab != null) {
                // 在+号之前插入
                val insertPosition = (chatTabLayout?.tabCount ?: 1) - 1
                chatTabLayout?.addTab(newTab, insertPosition)

                // 选中新创建的标签
                newTab.select()

                // 重新设置拖动功能
                setupTabDragAndDrop(chatTabLayout)
            }

            // 更新自定义标签页的保存顺序
            updateCustomTabsOrder()

            // 保存自定义标签页配置
            saveCustomTabConfiguration(groupName)

            Log.d(TAG, "创建自定义分组: $groupName")
        } catch (e: Exception) {
            Log.e(TAG, "创建自定义分组失败", e)
            Toast.makeText(this, "创建分组失败", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 显示自定义分组中的联系人
     */
    private fun showCustomGroupContacts(groupName: String) {
        try {
            val customCategory = allContacts.find { it.name == groupName }

            val displayCategories = if (customCategory != null && customCategory.contacts.isNotEmpty()) {
                listOf(customCategory.copy(isExpanded = true))
            } else {
                // 如果分组为空，显示空的分组（不添加提示对象）
                listOf(ContactCategory(
                    name = groupName,
                    contacts = mutableListOf(),
                    isExpanded = true
                ))
            }

            chatContactAdapter?.updateContacts(displayCategories)
            Log.d(TAG, "显示自定义分组: $groupName，包含${customCategory?.contacts?.size ?: 0}个AI")
        } catch (e: Exception) {
            Log.e(TAG, "显示自定义分组联系人失败", e)
        }
    }

    /**
     * 加载自定义标签页（按保存的顺序）
     */
    private fun loadCustomTabs() {
        try {
            val prefs = getSharedPreferences("custom_tabs", MODE_PRIVATE)
            val customTabs = prefs.getString("custom_ai_tabs", "") ?: ""

            if (customTabs.isNotEmpty()) {
                val chatTabLayout = findViewById<com.google.android.material.tabs.TabLayout>(R.id.chat_tab_layout)
                val tabNames = customTabs.split(",")

                Log.d(TAG, "加载自定义标签页: ${tabNames.joinToString(", ")}")

                // 按照保存的顺序依次添加标签
                tabNames.forEach { tabName ->
                    if (tabName.isNotEmpty() && tabName != "AI助手") {
                        // 确保allContacts中存在对应的分组
                        val existingCategory = allContacts.find { it.name == tabName }
                        if (existingCategory == null) {
                            // 如果allContacts中不存在，创建一个空分组
                            val newCategory = ContactCategory(
                                name = tabName,
                                contacts = mutableListOf(),
                                isExpanded = true
                            )
                            allContacts.add(newCategory)
                            Log.d(TAG, "为标签页创建对应分组: $tabName")
                        }

                        val newTab = chatTabLayout?.newTab()?.setText(tabName)
                        if (newTab != null) {
                            // 在+号之前插入
                            val insertPosition = (chatTabLayout?.tabCount ?: 1) - 1
                            chatTabLayout?.addTab(newTab, insertPosition)
                        }
                    }
                }

                // 重新设置拖动功能（因为添加了新标签）
                setupTabDragAndDrop(chatTabLayout)
            }
        } catch (e: Exception) {
            Log.e(TAG, "加载自定义标签页失败", e)
        }
    }

    /**
     * 查找联系人所在的分组（增强版，支持AI联系人）
     */
    private fun findContactGroup(contact: ChatContact): String? {
        try {
            Log.d(TAG, "查找联系人分组: ${contact.name} (ID: ${contact.id})")

            // 1. 首先在allContacts中查找
            allContacts.forEach { category ->
                if (category.contacts.any { it.id == contact.id }) {
                    Log.d(TAG, "在allContacts中找到联系人 ${contact.name} 在分组: ${category.name}")
                    return category.name
                }
            }

            // 2. 如果是AI联系人且没找到，检查当前显示的标签页
            if (contact.type == ContactType.AI) {
                val chatTabLayout = findViewById<com.google.android.material.tabs.TabLayout>(R.id.chat_tab_layout)
                val currentTabPosition = chatTabLayout?.selectedTabPosition ?: 0
                val currentTab = chatTabLayout?.getTabAt(currentTabPosition)
                val currentTabText = currentTab?.text?.toString()

                Log.d(TAG, "AI联系人未在allContacts中找到，当前标签页: $currentTabText")

                when (currentTabPosition) {
                    0 -> {
                        // 在"全部"标签页中，AI联系人应该属于AI助手分组
                        Log.d(TAG, "AI在全部标签页中，归类为AI助手")
                        return "AI助手"
                    }
                    1 -> {
                        // 在"AI助手"标签页中
                        Log.d(TAG, "AI在AI助手标签页中")
                        return "AI助手"
                    }
                    else -> {
                        // 在自定义标签页中
                        if (currentTabText != null && currentTabText != "+") {
                            Log.d(TAG, "AI在自定义标签页中: $currentTabText")
                            return currentTabText
                        } else {
                            // 如果是无效的自定义标签页，默认归类为AI助手
                            Log.d(TAG, "AI在无效标签页中，默认归类为AI助手")
                            return "AI助手"
                        }
                    }
                }
            }

            Log.d(TAG, "未找到联系人 ${contact.name} 的分组")
            return null

        } catch (e: Exception) {
            Log.e(TAG, "查找联系人分组失败", e)
            return null
        }
    }

    /**
     * 显示为联系人创建新分组的对话框（优化版）
     */
    private fun showCreateNewGroupForContactDialog(contact: ChatContact) {
        try {
            val input = android.widget.EditText(this).apply {
                hint = "例如：工作AI、学习AI、娱乐AI"
                setPadding(50, 30, 50, 30)
                // 设置输入提示
                setHint("输入新分组名称")
            }

            val message = """
                为 ${contact.name} 创建新的分组

                💡 建议分组名称：
                • 工作AI - 用于工作相关的AI助手
                • 学习AI - 用于学习和教育的AI
                • 生活AI - 用于日常生活的AI助手
                • 娱乐AI - 用于娱乐和休闲的AI

                或者根据您的需要自定义分组名称
            """.trimIndent()

            AlertDialog.Builder(this, R.style.Theme_MaterialDialog)
                .setTitle("创建新分组")
                .setMessage(message)
                .setView(input)
                .setPositiveButton("创建并移动") { _, _ ->
                    val groupName = input.text.toString().trim()
                    if (groupName.isNotEmpty()) {
                        if (isValidGroupName(groupName)) {
                            createCustomGroup(groupName)
                            moveContactToGroup(contact, groupName)
                            Toast.makeText(this, "✅ 已创建分组 \"$groupName\" 并移动 ${contact.name}", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(this, "分组名称不能包含特殊字符", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this, "分组名称不能为空", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("取消", null)
                .setNeutralButton("快速选择") { _, _ ->
                    showQuickGroupSelection(contact)
                }
                .show()
        } catch (e: Exception) {
            Log.e(TAG, "显示创建新分组对话框失败", e)
            Toast.makeText(this, "创建分组失败", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 验证分组名称是否有效
     */
    private fun isValidGroupName(groupName: String): Boolean {
        // 检查是否包含特殊字符或与现有分组重名
        if (groupName.contains("+") || groupName.contains("全部") || groupName.contains("未分组")) {
            return false
        }

        // 检查是否与现有分组重名
        val existingGroup = allContacts.find { it.name == groupName }
        if (existingGroup != null) {
            Toast.makeText(this, "分组 \"$groupName\" 已存在", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }

    /**
     * 显示快速分组选择
     */
    private fun showQuickGroupSelection(contact: ChatContact) {
        val quickGroups = arrayOf(
            "工作AI",
            "学习AI",
            "生活AI",
            "娱乐AI",
            "专业AI",
            "创意AI"
        )

        AlertDialog.Builder(this, R.style.Theme_MaterialDialog)
            .setTitle("快速选择分组")
            .setMessage("选择一个预设分组名称：")
            .setItems(quickGroups) { _, which ->
                val selectedGroup = quickGroups[which]
                if (isValidGroupName(selectedGroup)) {
                    createCustomGroup(selectedGroup)
                    moveContactToGroup(contact, selectedGroup)
                    Toast.makeText(this, "✅ 已创建分组 \"$selectedGroup\" 并移动 ${contact.name}", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 移动联系人到指定分组（重新实现，支持AI联系人）
     */
    private fun moveContactToGroup(contact: ChatContact, targetGroupName: String) {
        try {
            Log.d(TAG, "开始移动联系人: ${contact.name} 到分组: $targetGroupName")

            // 1. 获取当前分组
            val currentGroup = findContactGroup(contact)
            Log.d(TAG, "当前分组: $currentGroup")

            // 2. 从当前分组中移除（如果存在）
            if (currentGroup != null && currentGroup != "未分组") {
                val currentCategory = allContacts.find { it.name == currentGroup }
                if (currentCategory != null) {
                    val updatedContacts = currentCategory.contacts.filter { it.id != contact.id }.toMutableList()
                    val updatedCategory = currentCategory.copy(contacts = updatedContacts)
                    val index = allContacts.indexOf(currentCategory)
                    allContacts[index] = updatedCategory
                    Log.d(TAG, "从分组 $currentGroup 中移除了 ${contact.name}")

                    // 如果分组变空且不是预设分组，考虑删除分组
                    if (updatedContacts.isEmpty() &&
                        currentGroup != "AI助手" &&
                        currentGroup != "全部" &&
                        currentGroup != "未分组") {
                        Log.d(TAG, "分组 $currentGroup 已空，保留分组但移除对应标签页")
                        removeEmptyGroupTab(currentGroup)
                    }
                } else {
                    Log.w(TAG, "当前分组 $currentGroup 在allContacts中不存在")
                }
            } else {
                Log.d(TAG, "联系人 ${contact.name} 当前不在任何分组中或在未分组中")
            }

            // 3. 添加到目标分组
            var targetCategory = allContacts.find { it.name == targetGroupName }
            if (targetCategory != null) {
                // 目标分组存在，添加联系人
                val updatedContacts = targetCategory.contacts.toMutableList()
                // 检查是否已存在，避免重复添加
                if (!updatedContacts.any { it.id == contact.id }) {
                    updatedContacts.add(contact)
                    val updatedCategory = targetCategory.copy(contacts = updatedContacts)
                    val index = allContacts.indexOf(targetCategory)
                    allContacts[index] = updatedCategory
                    Log.d(TAG, "将 ${contact.name} 添加到现有分组 $targetGroupName")
                } else {
                    Log.d(TAG, "${contact.name} 已在目标分组 $targetGroupName 中")
                }
            } else {
                // 目标分组不存在，创建新分组
                val newCategory = ContactCategory(
                    name = targetGroupName,
                    contacts = mutableListOf(contact),
                    isExpanded = true
                )
                allContacts.add(newCategory)
                Log.d(TAG, "创建新分组 $targetGroupName 并添加 ${contact.name}")

                // 如果是"AI助手"分组，清除删除标记和旧数据
                if (targetGroupName == "AI助手") {
                    val prefs = getSharedPreferences("custom_tabs", MODE_PRIVATE)
                    prefs.edit()
                        .putBoolean("ai_assistant_group_deleted", false)
                        .remove("ai_assistant_group_contacts") // 清理旧的联系人数据
                        .apply()
                    Log.d(TAG, "重新创建AI助手分组，清除删除标记和旧数据")

                    // 确保AI助手标签页存在
                    ensureAIAssistantTabExists()
                }
            }

            // 4. 确保目标分组有对应的标签页
            ensureTabForGroup(targetGroupName)

            // 5. 如果是"AI助手"分组，确保标签页存在
            if (targetGroupName == "AI助手") {
                ensureAIAssistantTabExists()
            }

            // 5. 更新AI的分组信息（如果是AI联系人）
            if (contact.type == ContactType.AI) {
                updateAIGroupInfo(contact, targetGroupName)
            }

            // 6. 保存更改
            saveContacts()
            Log.d(TAG, "保存联系人数据完成")

            // 7. 保存更改
            saveContacts()

            // 8. 确保目标分组有对应的标签页
            ensureTabForGroup(targetGroupName)

            // 9. 切换到目标标签页（如果存在）
            switchToTabIfExists(targetGroupName)

            // 10. 延迟刷新显示，确保标签页切换完成
            Handler(Looper.getMainLooper()).postDelayed({
                refreshCurrentTabDisplay()
                Log.d(TAG, "切换到目标分组并刷新显示完成: $targetGroupName")
            }, 200)

            Log.d(TAG, "移动联系人完成: ${contact.name} -> $targetGroupName")

            // 9. 显示成功提示
            val message = if (currentGroup != null && currentGroup != "未分组") {
                "✅ 已将 ${contact.name} 从 \"$currentGroup\" 移动到 \"$targetGroupName\""
            } else {
                "✅ 已将 ${contact.name} 移动到分组 \"$targetGroupName\""
            }
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            Log.d(TAG, "移动联系人成功: ${contact.name} 从 $currentGroup 到 $targetGroupName")

        } catch (e: Exception) {
            Log.e(TAG, "移动联系人到分组失败", e)
            Toast.makeText(this, "❌ 移动失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 确保分组有对应的标签页
     */
    private fun ensureTabForGroup(groupName: String) {
        try {
            val chatTabLayout = findViewById<com.google.android.material.tabs.TabLayout>(R.id.chat_tab_layout)

            // 检查是否已存在该标签页
            var tabExists = false
            for (i in 0 until (chatTabLayout?.tabCount ?: 0)) {
                val tab = chatTabLayout?.getTabAt(i)
                if (tab?.text?.toString() == groupName) {
                    tabExists = true
                    break
                }
            }

            // 如果不存在且不是预设分组，创建新标签页
            if (!tabExists && groupName != "全部" && groupName != "AI助手" && groupName != "未分组") {
                val tabCount = chatTabLayout?.tabCount ?: 0
                val insertPosition = if (tabCount > 0) tabCount - 1 else 0 // 在+号前插入

                val newTab = chatTabLayout?.newTab()?.setText(groupName)
                if (newTab != null) {
                    chatTabLayout.addTab(newTab, insertPosition)

                    // 重新设置拖拽监听器
                    setupTabDragListener(chatTabLayout)

                    // 保存标签页顺序
                    val currentTabOrderString = getCurrentTabOrder()
                    val currentTabOrder = if (currentTabOrderString.isNotEmpty()) {
                        currentTabOrderString.split(", ")
                    } else {
                        emptyList()
                    }
                    updateAllTabOrder(currentTabOrder)

                    Log.d(TAG, "为分组 $groupName 创建了新标签页")
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "确保分组标签页失败", e)
        }
    }

    /**
     * 切换到指定标签页（如果存在）
     */
    private fun switchToTabIfExists(tabName: String) {
        try {
            val chatTabLayout = findViewById<com.google.android.material.tabs.TabLayout>(R.id.chat_tab_layout)

            // 查找对应的标签页
            for (i in 0 until (chatTabLayout?.tabCount ?: 0)) {
                val tab = chatTabLayout?.getTabAt(i)
                if (tab?.text?.toString() == tabName) {
                    // 找到对应标签页，切换到该标签页
                    chatTabLayout.selectTab(tab)
                    Log.d(TAG, "切换到标签页: $tabName")
                    return
                }
            }

            // 如果是特殊分组名称，映射到对应标签页
            when (tabName) {
                "AI助手" -> {
                    val aiTab = chatTabLayout?.getTabAt(1)
                    if (aiTab != null) {
                        chatTabLayout.selectTab(aiTab)
                        Log.d(TAG, "切换到AI助手标签页")
                    }
                }
                "未分组" -> {
                    val allTab = chatTabLayout?.getTabAt(0)
                    if (allTab != null) {
                        chatTabLayout.selectTab(allTab)
                        Log.d(TAG, "切换到全部标签页")
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "切换标签页失败", e)
        }
    }



    /**
     * 显示全部标签页管理选项
     */
    private fun showAllTabManagement() {
        try {
            val allAICount = getAllAvailableAIs().size
            val groupCount = allContacts.count { it.name != "全部" && it.contacts.any { contact -> contact.type == ContactType.AI } }

            val options = arrayOf(
                "📊 查看统计信息 ($allAICount 个AI，$groupCount 个分组)",
                "🔄 刷新所有分组",
                "📋 导出分组配置",
                "📥 导入分组配置"
            )

            AlertDialog.Builder(this, R.style.Theme_MaterialDialog)
                .setTitle("全部 - 聚合视图管理")
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> showAllAIStatistics()
                        1 -> {
                            refreshAllGroups()
                            Toast.makeText(this, "已刷新所有分组", Toast.LENGTH_SHORT).show()
                        }
                        2 -> exportGroupConfiguration()
                        3 -> importGroupConfiguration()
                    }
                }
                .setNegativeButton("取消", null)
                .show()
        } catch (e: Exception) {
            Log.e(TAG, "显示全部标签页管理失败", e)
        }
    }

    /**
     * 显示所有AI统计信息
     */
    private fun showAllAIStatistics() {
        try {
            val allAIs = getAllAvailableAIs()
            val groupedAIs = allAIs.groupBy { findContactGroup(it) ?: "未分组" }

            val statisticsText = buildString {
                appendLine("📊 AI助手统计信息")
                appendLine("=".repeat(30))
                appendLine("总计: ${allAIs.size} 个AI助手")
                appendLine("分组数量: ${groupedAIs.size} 个")
                appendLine()

                groupedAIs.forEach { (groupName, ais) ->
                    appendLine("📁 $groupName: ${ais.size} 个AI")
                    ais.forEach { ai ->
                        val status = if (ai.isOnline) "🟢" else "🔴"
                        val pinned = if (ai.isPinned) "📌" else ""
                        val muted = if (ai.isMuted) "🔇" else ""
                        appendLine("  $status $pinned$muted ${ai.name}")
                    }
                    appendLine()
                }
            }

            AlertDialog.Builder(this, R.style.Theme_MaterialDialog)
                .setTitle("AI助手统计")
                .setMessage(statisticsText)
                .setPositiveButton("确定", null)
                .show()
        } catch (e: Exception) {
            Log.e(TAG, "显示AI统计信息失败", e)
        }
    }

    /**
     * 刷新所有分组
     */
    private fun refreshAllGroups() {
        try {
            loadInitialContacts()
            refreshCurrentTabDisplay()
            chatContactAdapter?.updateContacts(allContacts)
            Log.d(TAG, "刷新所有分组完成")
        } catch (e: Exception) {
            Log.e(TAG, "刷新所有分组失败", e)
        }
    }

    /**
     * 导出分组配置
     */
    private fun exportGroupConfiguration() {
        try {
            Toast.makeText(this, "导出功能开发中...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "导出分组配置失败", e)
        }
    }

    /**
     * 导入分组配置
     */
    private fun importGroupConfiguration() {
        try {
            Toast.makeText(this, "导入功能开发中...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "导入分组配置失败", e)
        }
    }

    /**
     * 更新AI的分组信息
     */
    private fun updateAIGroupInfo(aiContact: ChatContact, groupName: String) {
        try {
            // 更新AI联系人的分组标签信息
            // 这里可以扩展为更新AI的元数据或其他相关信息
            Log.d(TAG, "更新AI ${aiContact.name} 的分组信息为: $groupName")

            // 如果需要，可以在这里更新AI的其他属性
            // 例如：aiContact.groupTag = groupName

        } catch (e: Exception) {
            Log.e(TAG, "更新AI分组信息失败", e)
        }
    }

    /**
     * 刷新所有标签页显示
     */
    private fun refreshAllTabDisplay() {
        try {
            // 刷新当前标签页显示
            refreshCurrentTabDisplay()
            // 更新适配器
            chatContactAdapter?.updateContacts(allContacts)
            Log.d(TAG, "刷新所有标签页显示完成")
        } catch (e: Exception) {
            Log.e(TAG, "刷新所有标签页显示失败", e)
        }
    }

    /**
     * 刷新所有标签页数据（重新加载和同步数据）
     */
    private fun refreshAllTabsData() {
        try {
            // 重新加载联系人数据
            loadInitialContacts()

            // 确保数据一致性
            ensureDataConsistency()

            Log.d(TAG, "刷新所有标签页数据完成")
        } catch (e: Exception) {
            Log.e(TAG, "刷新所有标签页数据失败", e)
        }
    }

    /**
     * 确保数据一致性
     */
    private fun ensureDataConsistency() {
        try {
            // 清理无效的AI联系人引用
            allContacts.forEach { category ->
                val validContacts = category.contacts.filter { contact ->
                    // 保留有效的AI联系人
                    contact.type == ContactType.AI &&
                    !contact.id.contains("hint") &&
                    !contact.id.contains("empty")
                }.toMutableList()

                if (validContacts.size != category.contacts.size) {
                    val updatedCategory = category.copy(contacts = validContacts)
                    val index = allContacts.indexOf(category)
                    allContacts[index] = updatedCategory
                    Log.d(TAG, "清理分组 ${category.name} 中的无效联系人")
                }
            }

            // 移除空的自定义分组（保留系统分组）
            val categoriesToRemove = allContacts.filter { category ->
                category.contacts.isEmpty() &&
                category.name != "全部" &&
                category.name != "AI助手" &&
                category.name != "提示"
            }

            categoriesToRemove.forEach { category ->
                allContacts.remove(category)
                Log.d(TAG, "移除空分组: ${category.name}")
            }

        } catch (e: Exception) {
            Log.e(TAG, "确保数据一致性失败", e)
        }
    }

    // 已移除markAllAIsAsUnread()方法 - 不再提供批量标记未读功能

    /**
     * 显示AI助手分组管理
     */
    private fun showAIAssistantGroupManagement(tab: com.google.android.material.tabs.TabLayout.Tab) {
        try {
            val aiAssistantCategory = allContacts.find { it.name == "AI助手" }
            val aiCount = aiAssistantCategory?.contacts?.size ?: 0

            val options = arrayOf(
                "📊 查看分组信息",
                "✏️ 重命名分组",
                "🔔 设为未读",
                "🗑️ 删除分组"
            )

            AlertDialog.Builder(this, R.style.Theme_MaterialDialog)
                .setTitle("管理AI助手分组")
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> showAIAssistantGroupInfo(aiCount)
                        1 -> showRenameGroupDialog(tab, "AI助手")
                        2 -> markAIAssistantGroupAsUnread()
                        3 -> showDeleteAIAssistantGroupDialog(tab, aiCount)
                    }
                }
                .setNegativeButton("取消", null)
                .show()
        } catch (e: Exception) {
            Log.e(TAG, "显示AI助手分组管理失败", e)
        }
    }

    /**
     * 显示AI助手分组信息
     */
    private fun showAIAssistantGroupInfo(aiCount: Int) {
        try {
            val aiAssistantCategory = allContacts.find { it.name == "AI助手" }
            val aiList = aiAssistantCategory?.contacts?.joinToString("\n") { "• ${it.name}" } ?: "无"

            val message = """
                🤖 AI助手分组信息

                这是您的专属AI助手分组
                当前包含: $aiCount 个AI助手

                包含的AI:
                $aiList

                💡 您可以：
                • 长按其他分组中的AI移动到这里
                • 重命名或删除这个分组
                • 在搜索时只向此分组中的AI提问
            """.trimIndent()

            AlertDialog.Builder(this, R.style.Theme_MaterialDialog)
                .setTitle("AI助手分组")
                .setMessage(message)
                .setPositiveButton("确定", null)
                .show()
        } catch (e: Exception) {
            Log.e(TAG, "显示AI助手分组信息失败", e)
        }
    }

    /**
     * 显示删除AI助手分组对话框
     */
    private fun showDeleteAIAssistantGroupDialog(tab: com.google.android.material.tabs.TabLayout.Tab, aiCount: Int) {
        try {
            val message = if (aiCount > 0) {
                "AI助手分组中还有 $aiCount 个AI助手。\n\n删除分组后，这些AI将回到\"全部\"标签中。\n\n确定要删除AI助手分组吗？"
            } else {
                "确定要删除空的AI助手分组吗？\n\n删除后，您可以重新创建新的分组。"
            }

            AlertDialog.Builder(this, R.style.Theme_MaterialDialog)
                .setTitle("删除AI助手分组")
                .setMessage(message)
                .setPositiveButton("删除") { _, _ ->
                    deleteAIAssistantGroup(tab)
                }
                .setNegativeButton("取消", null)
                .show()
        } catch (e: Exception) {
            Log.e(TAG, "显示删除AI助手分组对话框失败", e)
        }
    }

    /**
     * 删除AI助手分组
     */
    private fun deleteAIAssistantGroup(tab: com.google.android.material.tabs.TabLayout.Tab) {
        try {
            // 找到AI助手分组
            val aiAssistantCategory = allContacts.find { it.name == "AI助手" }
            if (aiAssistantCategory != null) {
                // AI助手分组中的AI将保留在系统中，但不再属于任何特定分组
                // 它们将在"全部"标签中显示
                Log.d(TAG, "AI助手分组中有 ${aiAssistantCategory.contacts.size} 个AI将保留在全部标签中")

                // 移除AI助手分组
                allContacts.remove(aiAssistantCategory)

                // 保存联系人数据
                saveContacts()
            }

            // 记录用户主动删除了AI助手分组，并清理相关数据
            val prefs = getSharedPreferences("custom_tabs", MODE_PRIVATE)
            prefs.edit()
                .putBoolean("ai_assistant_group_deleted", true)
                .remove("ai_assistant_group_contacts") // 清理AI助手分组的联系人数据
                .apply()

            Log.d(TAG, "清理AI助手分组相关数据")

            // 从TabLayout中移除标签
            val chatTabLayout = findViewById<com.google.android.material.tabs.TabLayout>(R.id.chat_tab_layout)
            chatTabLayout?.removeTab(tab)

            // 从SharedPreferences中移除AI助手标签页配置
            removeCustomTabFromPreferences("AI助手")

            // 切换到"全部"标签页
            chatTabLayout?.getTabAt(0)?.select()

            // 延迟刷新显示，避免立即触发弹窗
            Handler(Looper.getMainLooper()).postDelayed({
                showAllUserAIContacts() // 直接显示全部AI，避免触发管理弹窗
                Log.d(TAG, "删除AI助手分组后刷新全部标签页显示完成")
            }, 300)

            Toast.makeText(this, "AI助手分组已删除，AI已回到\"全部\"中", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "删除AI助手分组完成")

        } catch (e: Exception) {
            Log.e(TAG, "删除AI助手分组失败", e)
            Toast.makeText(this, "删除失败", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 显示自定义分组管理
     */
    private fun showCustomGroupManagement(tab: com.google.android.material.tabs.TabLayout.Tab) {
        try {
            val groupName = tab.text?.toString() ?: return
            val category = allContacts.find { it.name == groupName }
            val aiCount = category?.contacts?.size ?: 0

            val options = arrayOf(
                "📊 查看分组信息",
                "✏️ 重命名分组",
                "🔔 设为未读",
                "🗑️ 删除分组"
            )

            AlertDialog.Builder(this, R.style.Theme_MaterialDialog)
                .setTitle("管理分组: $groupName")
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> showGroupInfo(groupName, aiCount)
                        1 -> showRenameGroupDialog(tab, groupName)
                        2 -> markCustomGroupAsUnread(groupName)
                        3 -> showDeleteGroupDialog(tab, groupName, aiCount)
                    }
                }
                .setNegativeButton("取消", null)
                .show()
        } catch (e: Exception) {
            Log.e(TAG, "显示自定义分组管理失败", e)
        }
    }

    /**
     * 显示分组信息
     */
    private fun showGroupInfo(groupName: String, aiCount: Int) {
        try {
            val category = allContacts.find { it.name == groupName }
            val aiList = category?.contacts?.joinToString("\n") { "• ${it.name}" } ?: "无"

            val message = """
                📁 分组信息

                分组名称: $groupName
                AI数量: $aiCount

                包含的AI:
                $aiList

                💡 您可以通过长按AI来移动它们到其他分组
            """.trimIndent()

            AlertDialog.Builder(this, R.style.Theme_MaterialDialog)
                .setTitle("分组详情")
                .setMessage(message)
                .setPositiveButton("确定", null)
                .show()
        } catch (e: Exception) {
            Log.e(TAG, "显示分组信息失败", e)
        }
    }

    /**
     * 显示重命名分组对话框
     */
    private fun showRenameGroupDialog(tab: com.google.android.material.tabs.TabLayout.Tab, currentName: String) {
        try {
            val input = android.widget.EditText(this).apply {
                hint = "输入新的分组名称"
                setText(currentName)
                selectAll()
                setPadding(50, 30, 50, 30)
            }

            AlertDialog.Builder(this, R.style.Theme_MaterialDialog)
                .setTitle("重命名分组")
                .setMessage("为分组输入新名称")
                .setView(input)
                .setPositiveButton("确定") { _, _ ->
                    val newName = input.text.toString().trim()
                    if (newName.isNotEmpty() && newName != currentName) {
                        renameGroup(tab, currentName, newName)
                    } else if (newName.isEmpty()) {
                        Toast.makeText(this, "分组名称不能为空", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("取消", null)
                .show()
        } catch (e: Exception) {
            Log.e(TAG, "显示重命名分组对话框失败", e)
        }
    }

    /**
     * 显示删除分组对话框
     */
    private fun showDeleteGroupDialog(tab: com.google.android.material.tabs.TabLayout.Tab, groupName: String, aiCount: Int) {
        try {
            val message = if (aiCount > 0) {
                "分组 \"$groupName\" 中还有 $aiCount 个AI助手。\n\n删除分组后，这些AI将被移动到\"AI助手\"分组中。\n\n确定要删除这个分组吗？"
            } else {
                "确定要删除空分组 \"$groupName\" 吗？"
            }

            AlertDialog.Builder(this, R.style.Theme_MaterialDialog)
                .setTitle("删除分组")
                .setMessage(message)
                .setPositiveButton("删除") { _, _ ->
                    deleteGroup(tab, groupName)
                }
                .setNegativeButton("取消", null)
                .show()
        } catch (e: Exception) {
            Log.e(TAG, "显示删除分组对话框失败", e)
        }
    }

    /**
     * 重命名分组
     */
    private fun renameGroup(tab: com.google.android.material.tabs.TabLayout.Tab, oldName: String, newName: String) {
        try {
            // 检查新名称是否已存在
            val existingCategory = allContacts.find { it.name == newName }
            if (existingCategory != null) {
                Toast.makeText(this, "分组 \"$newName\" 已存在", Toast.LENGTH_SHORT).show()
                return
            }

            // 更新ContactCategory中的名称
            val categoryIndex = allContacts.indexOfFirst { it.name == oldName }
            if (categoryIndex != -1) {
                val oldCategory = allContacts[categoryIndex]
                val newCategory = oldCategory.copy(name = newName)
                allContacts[categoryIndex] = newCategory
            }

            // 更新TabLayout中的标签文本
            tab.text = newName

            // 更新SharedPreferences中的自定义标签页配置
            updateCustomTabName(oldName, newName)

            // 保存联系人数据
            saveContacts()

            Toast.makeText(this, "分组已重命名为 \"$newName\"", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "重命名分组: $oldName -> $newName")

        } catch (e: Exception) {
            Log.e(TAG, "重命名分组失败", e)
            Toast.makeText(this, "重命名失败", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 删除分组
     */
    private fun deleteGroup(tab: com.google.android.material.tabs.TabLayout.Tab, groupName: String) {
        try {
            // 找到要删除的分组
            val categoryToDelete = allContacts.find { it.name == groupName }
            if (categoryToDelete != null) {
                // 如果分组中有AI，将它们移动到"AI助手"分组
                if (categoryToDelete.contacts.isNotEmpty()) {
                    val aiAssistantCategory = allContacts.find { it.name == "AI助手" }
                    if (aiAssistantCategory != null) {
                        val updatedContacts = aiAssistantCategory.contacts.toMutableList()
                        updatedContacts.addAll(categoryToDelete.contacts)
                        val updatedCategory = aiAssistantCategory.copy(contacts = updatedContacts)
                        val index = allContacts.indexOf(aiAssistantCategory)
                        allContacts[index] = updatedCategory
                    } else {
                        // 如果"AI助手"分组不存在，创建它
                        val newAIAssistantCategory = ContactCategory(
                            name = "AI助手",
                            contacts = categoryToDelete.contacts,
                            isExpanded = true
                        )
                        allContacts.add(newAIAssistantCategory)

                        // 确保AI助手标签页存在
                        ensureAIAssistantTabExists()
                    }
                }

                // 从allContacts中移除分组
                allContacts.remove(categoryToDelete)
            }

            // 从TabLayout中移除标签
            val chatTabLayout = findViewById<com.google.android.material.tabs.TabLayout>(R.id.chat_tab_layout)
            chatTabLayout?.removeTab(tab)

            // 从SharedPreferences中移除自定义标签页配置
            removeCustomTabFromPreferences(groupName)

            // 保存联系人数据
            saveContacts()

            // 切换到"全部"标签页
            chatTabLayout?.getTabAt(0)?.select()

            // 延迟刷新显示，避免立即触发弹窗
            Handler(Looper.getMainLooper()).postDelayed({
                showAllUserAIContacts() // 直接显示全部AI，避免触发管理弹窗
                Log.d(TAG, "删除分组后刷新全部标签页显示完成")
            }, 300)

            Toast.makeText(this, "分组 \"$groupName\" 已删除", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "删除分组: $groupName")

        } catch (e: Exception) {
            Log.e(TAG, "删除分组失败", e)
            Toast.makeText(this, "删除失败", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 更新自定义标签页名称
     */
    private fun updateCustomTabName(oldName: String, newName: String) {
        try {
            val prefs = getSharedPreferences("custom_tabs", MODE_PRIVATE)
            val customTabs = prefs.getString("custom_ai_tabs", "") ?: ""

            if (customTabs.isNotEmpty()) {
                val tabNames = customTabs.split(",").toMutableList()
                val index = tabNames.indexOf(oldName)
                if (index != -1) {
                    tabNames[index] = newName
                    prefs.edit().putString("custom_ai_tabs", tabNames.joinToString(",")).apply()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "更新自定义标签页名称失败", e)
        }
    }

    /**
     * 从SharedPreferences中移除自定义标签页
     */
    private fun removeCustomTabFromPreferences(groupName: String) {
        try {
            val prefs = getSharedPreferences("custom_tabs", MODE_PRIVATE)
            val editor = prefs.edit()

            // 1. 从custom_ai_tabs中移除
            val customTabs = prefs.getString("custom_ai_tabs", "") ?: ""
            if (customTabs.isNotEmpty()) {
                val tabNames = customTabs.split(",").toMutableList()
                tabNames.remove(groupName)
                editor.putString("custom_ai_tabs", tabNames.joinToString(","))
            }

            // 2. 从all_tab_order中移除
            val allTabOrder = prefs.getString("all_tab_order", "") ?: ""
            if (allTabOrder.isNotEmpty()) {
                val allTabs = allTabOrder.split(",").toMutableList()
                allTabs.remove(groupName)
                editor.putString("all_tab_order", allTabs.joinToString(","))
            }

            editor.apply()
            Log.d(TAG, "从SharedPreferences中移除标签页: $groupName")

        } catch (e: Exception) {
            Log.e(TAG, "从SharedPreferences中移除自定义标签页失败", e)
        }
    }

    /**
     * 保存自定义标签页配置
     */
    private fun saveCustomTabConfiguration(tabName: String) {
        try {
            val prefs = getSharedPreferences("custom_tabs", MODE_PRIVATE)
            val existingTabs = prefs.getString("custom_ai_tabs", "") ?: ""

            val tabList = if (existingTabs.isNotEmpty()) {
                existingTabs.split(",").toMutableList()
            } else {
                mutableListOf()
            }

            if (!tabList.contains(tabName)) {
                tabList.add(tabName)
                prefs.edit().putString("custom_ai_tabs", tabList.joinToString(",")).apply()
            }
        } catch (e: Exception) {
            Log.e(TAG, "保存自定义标签页配置失败", e)
        }
    }

    /**
     * 从分组中移除联系人（回到"全部"标签下）
     */
    private fun removeContactFromGroup(contact: ChatContact) {
        try {
            // 从当前分组中移除
            val currentGroup = findContactGroup(contact)
            if (currentGroup != null && currentGroup != "未分组") {
                val currentCategory = allContacts.find { it.name == currentGroup }
                if (currentCategory != null) {
                    val updatedContacts = currentCategory.contacts.filter { it.id != contact.id }.toMutableList()
                    val updatedCategory = currentCategory.copy(contacts = updatedContacts)
                    val index = allContacts.indexOf(currentCategory)
                    allContacts[index] = updatedCategory

                    // 如果分组变空且不是预设分组，移除对应标签页
                    if (updatedContacts.isEmpty() &&
                        currentGroup != "AI助手" &&
                        currentGroup != "全部" &&
                        currentGroup != "未分组") {
                        Log.d(TAG, "分组 $currentGroup 已空，移除对应标签页")
                        removeEmptyGroupTab(currentGroup)
                    }

                    // 保存更改
                    saveContacts()

                    // 刷新当前显示（不切换到"全部"标签页，避免弹窗）
                    refreshCurrentTabDisplay()

                    // 更新适配器
                    chatContactAdapter?.updateContacts(allContacts)

                    Toast.makeText(this, "✅ ${contact.name} 已移除分组，现在在\"全部\"中", Toast.LENGTH_LONG).show()
                    Log.d(TAG, "移除联系人分组: ${contact.name} 从 $currentGroup")
                } else {
                    Toast.makeText(this, "❌ 未找到联系人所在的分组", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "${contact.name} 已经在\"全部\"中", Toast.LENGTH_SHORT).show()
            }

        } catch (e: Exception) {
            Log.e(TAG, "移除联系人分组失败", e)
            Toast.makeText(this, "❌ 移除分组失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 移除空分组的标签页
     */
    private fun removeEmptyGroupTab(groupName: String) {
        try {
            val chatTabLayout = findViewById<com.google.android.material.tabs.TabLayout>(R.id.chat_tab_layout)

            // 查找并移除对应的标签页
            for (i in 0 until (chatTabLayout?.tabCount ?: 0)) {
                val tab = chatTabLayout?.getTabAt(i)
                if (tab?.text?.toString() == groupName) {
                    chatTabLayout?.removeTabAt(i)
                    Log.d(TAG, "移除空分组标签页: $groupName")
                    break
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "移除空分组标签页失败", e)
        }
    }

    /**
     * 获取所有可用的AI助手列表（从存储的数据中获取，保持状态一致性）
     */
    private fun getAllAvailableAIs(): List<ChatContact> {
        return try {
            val availableAIs = mutableListOf<ChatContact>()

            // 定义所有可用的AI助手
            val aiDefinitions = listOf(
                "DeepSeek" to "🚀 DeepSeek - 性能强劲，支持中文",
                "ChatGPT" to "🤖 ChatGPT - OpenAI的经典模型",
                "Claude" to "💡 Claude - Anthropic的智能助手",
                "Gemini" to "🌟 Gemini - Google的AI助手",
                "智谱AI" to "🧠 智谱AI - GLM-4大语言模型",
                "文心一言" to "📚 文心一言 - 百度的大语言模型",
                "通义千问" to "🎯 通义千问 - 阿里巴巴的AI",
                "讯飞星火" to "⚡ 讯飞星火 - 科大讯飞的AI",
                "Kimi" to "🌙 Kimi - Moonshot的长文本专家"
            )

            aiDefinitions.forEach { (aiName, description) ->
                // 使用与灵动岛相同的ID生成逻辑
                val processedName = if (aiName.contains(Regex("[\\u4e00-\\u9fff]"))) {
                    // 包含中文字符，直接使用原名称
                    aiName
                } else {
                    // 英文字符，转换为小写
                    aiName.lowercase()
                }
                val aiId = "ai_${processedName.replace(" ", "_")}"
                
                Log.d(TAG, "getAllAvailableAIs - AI名称: $aiName, 联系人ID: $aiId")

                // 首先尝试从allContacts中找到已存在的AI联系人
                var existingContact: ChatContact? = null
                allContacts.forEach { category ->
                    val found = category.contacts.find { it.id == aiId }
                    if (found != null) {
                        existingContact = found
                        return@forEach
                    }
                }

                val apiKey = getApiKeyForAI(aiName)
                val isConfigured = apiKey.isNotEmpty()

                val contact = if (existingContact != null) {
                    // 如果已存在，更新API配置信息但保持其他状态
                    existingContact!!.copy(
                        isOnline = isConfigured,
                        lastMessage = if (isConfigured) "API已配置，可以开始对话" else "点击配置API密钥",
                        customData = mapOf(
                            "api_url" to getDefaultApiUrl(aiName),
                            "api_key" to apiKey,
                            "model" to getDefaultModel(aiName),
                            "is_configured" to isConfigured.toString()
                        )
                    )
                } else {
                    // 如果不存在，创建新的AI联系人
                    ChatContact(
                        id = aiId,
                        name = aiName,
                        type = ContactType.AI,
                        description = description,
                        isOnline = isConfigured,
                        lastMessage = if (isConfigured) "API已配置，可以开始对话" else "点击配置API密钥",
                        lastMessageTime = System.currentTimeMillis(),
                        unreadCount = 0,
                        isPinned = false,
                        customData = mapOf(
                            "api_url" to getDefaultApiUrl(aiName),
                            "api_key" to apiKey,
                            "model" to getDefaultModel(aiName),
                            "is_configured" to isConfigured.toString()
                        ),
                        aiMembers = emptyList()
                    )
                }
                availableAIs.add(contact)
            }

            availableAIs
        } catch (e: Exception) {
            Log.e(TAG, "获取所有可用AI失败", e)
            emptyList()
        }
    }

    /**
     * 显示分组管理对话框（长按分组名称触发）
     */
    private fun showCategoryManagementDialog(category: ContactCategory) {
        try {
            val groupName = category.name
            val aiCount = category.contacts.size

            // 只有用户创建的分组和AI助手分组才显示管理选项
            if (groupName == "全部" || groupName == "提示" || groupName == "未分组") {
                Log.d(TAG, "系统分组不支持管理操作: $groupName")
                return
            }

            // 根据分组类型显示不同的管理选项
            val options = when (groupName) {
                "AI助手" -> {
                    // "AI助手"分组可以重命名、删除、标为未读
                    arrayOf(
                        "📊 查看分组信息 ($aiCount 个AI)",
                        "✏️ 重命名分组",
                        "🔔 标为未读",
                        "🗑️ 删除分组"
                    )
                }
                else -> {
                    // 自定义分组可以重命名、删除、标为未读
                    arrayOf(
                        "📊 查看分组信息 ($aiCount 个AI)",
                        "✏️ 重命名分组",
                        "🔔 标为未读",
                        "🗑️ 删除分组"
                    )
                }
            }

            AlertDialog.Builder(this, R.style.Theme_MaterialDialog)
                .setTitle("管理分组: $groupName")
                .setItems(options) { _, which ->
                    when (options[which]) {
                        "📊 查看分组信息" -> showCategoryInfo(category)
                        "✏️ 重命名分组" -> showRenameCategoryDialog(category)
                        "📌 置顶分组" -> toggleCategoryPin(category, true)
                        "🔔 标为未读" -> markCategoryAsUnread(category)
                        "🗑️ 删除分组" -> showDeleteCategoryDialog(category)
                    }
                }
                .setNegativeButton("取消", null)
                .show()
        } catch (e: Exception) {
            Log.e(TAG, "显示分组管理对话框失败", e)
        }
    }

    /**
     * 显示分组信息
     */
    private fun showCategoryInfo(category: ContactCategory) {
        try {
            val aiList = category.contacts.joinToString("\n") { "• ${it.name}" }
            val configuredCount = category.contacts.count { it.isOnline }

            val message = """
                📁 分组详情

                分组名称: ${category.name}
                AI数量: ${category.contacts.size}
                已配置: $configuredCount
                未配置: ${category.contacts.size - configuredCount}

                包含的AI:
                ${if (aiList.isNotEmpty()) aiList else "无"}

                💡 您可以：
                • 长按AI移动到其他分组
                • 重命名或删除这个分组
                • 在搜索时只向此分组中的AI提问
            """.trimIndent()

            AlertDialog.Builder(this, R.style.Theme_MaterialDialog)
                .setTitle("分组信息")
                .setMessage(message)
                .setPositiveButton("确定", null)
                .show()
        } catch (e: Exception) {
            Log.e(TAG, "显示分组信息失败", e)
        }
    }

    /**
     * 显示重命名分组对话框
     */
    private fun showRenameCategoryDialog(category: ContactCategory) {
        try {
            val input = android.widget.EditText(this).apply {
                hint = "输入新的分组名称"
                setText(category.name)
                selectAll()
                setPadding(50, 30, 50, 30)
            }

            AlertDialog.Builder(this, R.style.Theme_MaterialDialog)
                .setTitle("重命名分组")
                .setMessage("为分组输入新名称")
                .setView(input)
                .setPositiveButton("确定") { _, _ ->
                    val newName = input.text.toString().trim()
                    if (newName.isNotEmpty() && newName != category.name) {
                        renameCategoryInData(category, newName)
                    } else if (newName.isEmpty()) {
                        Toast.makeText(this, "分组名称不能为空", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("取消", null)
                .show()
        } catch (e: Exception) {
            Log.e(TAG, "显示重命名分组对话框失败", e)
        }
    }

    /**
     * 置顶分组
     */
    private fun toggleCategoryPin(category: ContactCategory, isPinned: Boolean) {
        try {
            // 找到分组并更新置顶状态
            val categoryIndex = allContacts.indexOfFirst { it.name == category.name }
            if (categoryIndex != -1) {
                val updatedCategory = category.copy(isPinned = isPinned)
                allContacts[categoryIndex] = updatedCategory

                // 重新排序：置顶的分组在前面
                allContacts.sortWith(compareByDescending<ContactCategory> { it.isPinned }
                    .thenBy { it.name })

                // 保存更改
                saveContacts()

                // 刷新显示
                refreshCurrentTabDisplay()

                val action = if (isPinned) "置顶" else "取消置顶"
                Toast.makeText(this, "分组 \"${category.name}\" 已$action", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "置顶分组失败", e)
            Toast.makeText(this, "操作失败", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 标记分组为未读
     */
    private fun markCategoryAsUnread(category: ContactCategory) {
        try {
            // 将分组中所有联系人标记为未读
            val categoryIndex = allContacts.indexOfFirst { it.name == category.name }
            if (categoryIndex != -1) {
                val updatedContacts = category.contacts.map { contact ->
                    contact.copy(unreadCount = contact.unreadCount + 1)
                }.toMutableList()
                val updatedCategory = category.copy(contacts = updatedContacts)
                allContacts[categoryIndex] = updatedCategory

                // 保存更改
                saveContacts()

                // 刷新显示
                refreshCurrentTabDisplay()

                Toast.makeText(this, "分组 \"${category.name}\" 已标为未读", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "标记分组为未读失败", e)
            Toast.makeText(this, "操作失败", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 显示删除分组对话框
     */
    private fun showDeleteCategoryDialog(category: ContactCategory) {
        try {
            val message = if (category.contacts.isNotEmpty()) {
                "分组 \"${category.name}\" 中还有 ${category.contacts.size} 个AI助手。\n\n删除分组后，这些AI将回到\"全部\"标签中。\n\n确定要删除这个分组吗？"
            } else {
                "确定要删除空分组 \"${category.name}\" 吗？"
            }

            AlertDialog.Builder(this, R.style.Theme_MaterialDialog)
                .setTitle("删除分组")
                .setMessage(message)
                .setPositiveButton("删除") { _, _ ->
                    deleteCategoryFromData(category)
                }
                .setNegativeButton("取消", null)
                .show()
        } catch (e: Exception) {
            Log.e(TAG, "显示删除分组对话框失败", e)
        }
    }

    /**
     * 在数据中重命名分组
     */
    private fun renameCategoryInData(category: ContactCategory, newName: String) {
        try {
            // 检查新名称是否已存在
            val existingCategory = allContacts.find { it.name == newName }
            if (existingCategory != null) {
                Toast.makeText(this, "分组 \"$newName\" 已存在", Toast.LENGTH_SHORT).show()
                return
            }

            // 更新ContactCategory中的名称
            val categoryIndex = allContacts.indexOfFirst { it.name == category.name }
            if (categoryIndex != -1) {
                val updatedCategory = category.copy(name = newName)
                allContacts[categoryIndex] = updatedCategory

                // 更新TabLayout中的标签文本（如果存在）
                val chatTabLayout = findViewById<com.google.android.material.tabs.TabLayout>(R.id.chat_tab_layout)
                for (i in 0 until (chatTabLayout?.tabCount ?: 0)) {
                    val tab = chatTabLayout?.getTabAt(i)
                    if (tab?.text?.toString() == category.name) {
                        tab.text = newName
                        break
                    }
                }

                // 更新SharedPreferences中的自定义标签页配置
                updateCustomTabName(category.name, newName)

                // 保存联系人数据
                saveContacts()

                // 刷新显示
                refreshCurrentTabDisplay()

                Toast.makeText(this, "分组已重命名为 \"$newName\"", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "重命名分组: ${category.name} -> $newName")
            }
        } catch (e: Exception) {
            Log.e(TAG, "重命名分组失败", e)
            Toast.makeText(this, "重命名失败", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 从数据中删除分组
     */
    private fun deleteCategoryFromData(category: ContactCategory) {
        try {
            // 移除分组
            allContacts.remove(category)

            // 从TabLayout中移除对应的标签（如果存在）
            val chatTabLayout = findViewById<com.google.android.material.tabs.TabLayout>(R.id.chat_tab_layout)
            for (i in 0 until (chatTabLayout?.tabCount ?: 0)) {
                val tab = chatTabLayout?.getTabAt(i)
                if (tab?.text?.toString() == category.name) {
                    chatTabLayout?.removeTab(tab)
                    break
                }
            }

            // 从SharedPreferences中移除自定义标签页配置
            removeCustomTabFromPreferences(category.name)

            // 保存联系人数据
            saveContacts()

            // 切换到"全部"标签页
            chatTabLayout?.getTabAt(0)?.select()

            Toast.makeText(this, "分组 \"${category.name}\" 已删除", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "删除分组: ${category.name}")

        } catch (e: Exception) {
            Log.e(TAG, "删除分组失败", e)
            Toast.makeText(this, "删除失败", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 将AI助手分组标记为未读
     */
    private fun markAIAssistantGroupAsUnread() {
        try {
            val aiAssistantCategory = allContacts.find { it.name == "AI助手" }
            if (aiAssistantCategory != null) {
                val categoryIndex = allContacts.indexOf(aiAssistantCategory)
                val updatedContacts = aiAssistantCategory.contacts.map { contact ->
                    contact.copy(unreadCount = contact.unreadCount + 1)
                }.toMutableList()
                val updatedCategory = aiAssistantCategory.copy(contacts = updatedContacts)
                allContacts[categoryIndex] = updatedCategory

                // 保存更改
                saveContacts()

                // 刷新显示
                refreshCurrentTabDisplay()

                Toast.makeText(this, "AI助手分组已标记为未读", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "AI助手分组不存在", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "标记AI助手分组为未读失败", e)
            Toast.makeText(this, "操作失败", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 将自定义分组标记为未读
     */
    private fun markCustomGroupAsUnread(groupName: String) {
        try {
            val customCategory = allContacts.find { it.name == groupName }
            if (customCategory != null) {
                val categoryIndex = allContacts.indexOf(customCategory)
                val updatedContacts = customCategory.contacts.map { contact ->
                    contact.copy(unreadCount = contact.unreadCount + 1)
                }.toMutableList()
                val updatedCategory = customCategory.copy(contacts = updatedContacts)
                allContacts[categoryIndex] = updatedCategory

                // 保存更改
                saveContacts()

                // 刷新显示
                refreshCurrentTabDisplay()

                Toast.makeText(this, "分组 \"$groupName\" 已标记为未读", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "分组 \"$groupName\" 不存在", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "标记自定义分组为未读失败", e)
            Toast.makeText(this, "操作失败", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 设置标签页拖动排序功能
     */
    private fun setupTabDragAndDrop(tabLayout: com.google.android.material.tabs.TabLayout?) {
        if (tabLayout == null) return

        try {
            // 为每个标签页设置长按监听器和拖拽功能
            for (i in 0 until tabLayout.tabCount) {
                val tab = tabLayout.getTabAt(i)
                val tabView = (tabLayout.getChildAt(0) as? ViewGroup)?.getChildAt(i)

                tabView?.setOnLongClickListener { view ->
                    val tabPosition = i
                    val tabText = tab?.text?.toString()

                    // 允许拖动所有标签（除了"+"按钮）
                    if (tabText != "+") {
                        startTabDragWithTouch(tabLayout, tabPosition, tabText ?: "", view)
                        true
                    } else {
                        false
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "设置标签页拖动功能失败", e)
        }
    }

    /**
     * 开始标签页拖动（使用触摸拖拽）
     */
    private fun startTabDragWithTouch(
        tabLayout: com.google.android.material.tabs.TabLayout,
        fromPosition: Int,
        tabText: String,
        dragView: View
    ) {
        try {
            // 获取所有可拖动的标签位置（排除"+"）
            val draggablePositions = mutableListOf<Int>()
            val draggableTexts = mutableListOf<String>()

            for (i in 0 until tabLayout.tabCount - 1) { // 从位置0开始，到倒数第二个（排除+号）
                val tab = tabLayout.getTabAt(i)
                val text = tab?.text?.toString()
                if (text != null && text != "+") {
                    draggablePositions.add(i)
                    draggableTexts.add(text)
                }
            }

            if (draggablePositions.size <= 1) {
                Toast.makeText(this, "只有一个标签，无法排序", Toast.LENGTH_SHORT).show()
                return
            }

            // 创建拖拽阴影
            val shadowBuilder = View.DragShadowBuilder(dragView)

            // 开始拖拽
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                dragView.startDragAndDrop(null, shadowBuilder, TabDragData(fromPosition, tabText), 0)
            } else {
                @Suppress("DEPRECATION")
                dragView.startDrag(null, shadowBuilder, TabDragData(fromPosition, tabText), 0)
            }

            // 设置拖拽监听器
            setupTabDragListener(tabLayout)

            // 移除拖动提示，让用户专注于拖拽操作

        } catch (e: Exception) {
            Log.e(TAG, "开始标签页拖动失败", e)
            // 如果拖拽失败，回退到对话框方式
            startTabDrag(tabLayout, fromPosition, tabText)
        }
    }

    /**
     * 开始标签页拖动（对话框方式，作为备用）
     */
    private fun startTabDrag(tabLayout: com.google.android.material.tabs.TabLayout, fromPosition: Int, tabText: String) {
        try {
            // 获取所有可拖动的标签位置（排除"+"）
            val draggablePositions = mutableListOf<Int>()
            val draggableTexts = mutableListOf<String>()

            for (i in 0 until tabLayout.tabCount - 1) { // 从位置0开始，到倒数第二个（排除+号）
                val tab = tabLayout.getTabAt(i)
                val text = tab?.text?.toString()
                if (text != null && text != "+") {
                    draggablePositions.add(i)
                    draggableTexts.add(text)
                }
            }

            if (draggablePositions.size <= 1) {
                Toast.makeText(this, "只有一个自定义分组，无法排序", Toast.LENGTH_SHORT).show()
                return
            }

            // 显示拖动排序对话框
            showTabReorderDialog(tabLayout, fromPosition, tabText, draggablePositions, draggableTexts)

        } catch (e: Exception) {
            Log.e(TAG, "开始标签页拖动失败", e)
        }
    }

    /**
     * 显示标签页重新排序对话框
     */
    private fun showTabReorderDialog(
        tabLayout: com.google.android.material.tabs.TabLayout,
        fromPosition: Int,
        tabText: String,
        draggablePositions: List<Int>,
        draggableTexts: List<String>
    ) {
        try {
            val currentIndex = draggableTexts.indexOf(tabText)
            if (currentIndex == -1) return

            val options = draggableTexts.mapIndexed { index, text ->
                if (index == currentIndex) {
                    "📍 $text (当前位置)"
                } else {
                    "📁 $text"
                }
            }.toTypedArray()

            AlertDialog.Builder(this, R.style.Theme_MaterialDialog)
                .setTitle("调整 \"$tabText\" 的位置")
                .setMessage("选择要移动到的位置：")
                .setItems(options) { _, which ->
                    if (which != currentIndex) {
                        val targetPosition = draggablePositions[which]
                        moveTab(tabLayout, fromPosition, targetPosition, tabText)
                    }
                }
                .setNegativeButton("取消", null)
                .show()
        } catch (e: Exception) {
            Log.e(TAG, "显示标签页重新排序对话框失败", e)
        }
    }

    /**
     * 移动标签页位置
     */
    private fun moveTab(
        tabLayout: com.google.android.material.tabs.TabLayout,
        fromPosition: Int,
        toPosition: Int,
        tabText: String
    ) {
        try {
            Log.d(TAG, "开始移动标签: $tabText 从位置 $fromPosition 到 $toPosition")

            // 收集所有标签的信息
            val allTabs = mutableListOf<String>()
            for (i in 0 until tabLayout.tabCount) {
                val tab = tabLayout.getTabAt(i)
                val text = tab?.text?.toString()
                if (text != null && text != "+") {
                    allTabs.add(text)
                }
            }

            Log.d(TAG, "移动前标签列表: ${allTabs.joinToString(", ")}")

            // 在列表中移动标签
            if (fromPosition < allTabs.size && toPosition < allTabs.size) {
                val movedTab = allTabs.removeAt(fromPosition)
                allTabs.add(toPosition, movedTab)
            }

            Log.d(TAG, "移动后标签列表: ${allTabs.joinToString(", ")}")

            // 清除所有标签（保留+号）
            val hasPlusTab = tabLayout.tabCount > 0 &&
                            tabLayout.getTabAt(tabLayout.tabCount - 1)?.text?.toString() == "+"

            // 从后往前移除所有标签
            while (tabLayout.tabCount > 0) {
                tabLayout.removeTabAt(0)
            }

            // 重新添加所有标签
            allTabs.forEachIndexed { index, tabName ->
                val newTab = tabLayout.newTab().setText(tabName)
                tabLayout.addTab(newTab, index)
            }

            // 重新添加+号标签（如果之前存在）
            if (hasPlusTab) {
                val plusTab = tabLayout.newTab().setText("+").setIcon(R.drawable.ic_add)
                tabLayout.addTab(plusTab)
            }

            // 重新设置拖拽监听器
            setupTabDragAndDrop(tabLayout)

            // 更新标签页顺序到SharedPreferences
            updateAllTabOrder(allTabs)

            // 选中移动后的标签
            val targetTab = tabLayout.getTabAt(toPosition)
            targetTab?.select()

            Log.d(TAG, "标签移动完成: $tabText")

        } catch (e: Exception) {
            Log.e(TAG, "移动标签页失败", e)
            Toast.makeText(this, "移动失败", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 更新所有标签页顺序到SharedPreferences
     */
    private fun updateAllTabOrder(allTabs: List<String>) {
        try {
            val prefs = getSharedPreferences("custom_tabs", MODE_PRIVATE)
            val editor = prefs.edit()

            // 保存所有标签的顺序
            editor.putString("all_tab_order", allTabs.joinToString(","))

            // 分离自定义标签（排除"全部"和"AI助手"）
            val customTabs = allTabs.filter { it != "全部" && it != "AI助手" }
            editor.putString("custom_ai_tabs", customTabs.joinToString(","))

            editor.apply()

            Log.d(TAG, "更新所有标签页顺序: ${allTabs.joinToString(", ")}")
            Log.d(TAG, "更新自定义标签页顺序: ${customTabs.joinToString(", ")}")

        } catch (e: Exception) {
            Log.e(TAG, "更新标签页顺序失败", e)
        }
    }

    /**
     * 更新标签页顺序到SharedPreferences（保持向后兼容）
     */
    private fun updateTabOrder(tabLayout: com.google.android.material.tabs.TabLayout) {
        try {
            val customTabs = mutableListOf<String>()

            // 收集所有自定义标签（排除"全部"、"AI助手"和"+"）
            for (i in 2 until tabLayout.tabCount - 1) {
                val tab = tabLayout.getTabAt(i)
                val text = tab?.text?.toString()
                if (text != null && text != "+") {
                    customTabs.add(text)
                }
            }

            // 保存到SharedPreferences
            val prefs = getSharedPreferences("custom_tabs", MODE_PRIVATE)
            prefs.edit().putString("custom_ai_tabs", customTabs.joinToString(",")).apply()

            Log.d(TAG, "更新标签页顺序: ${customTabs.joinToString(", ")}")

        } catch (e: Exception) {
            Log.e(TAG, "更新标签页顺序失败", e)
        }
    }

    /**
     * 更新自定义标签页顺序（从当前TabLayout读取）
     */
    private fun updateCustomTabsOrder() {
        try {
            val chatTabLayout = findViewById<com.google.android.material.tabs.TabLayout>(R.id.chat_tab_layout)
            val customTabs = mutableListOf<String>()

            // 收集所有自定义标签（排除"全部"、"AI助手"和"+"）
            for (i in 2 until (chatTabLayout?.tabCount ?: 0) - 1) {
                val tab = chatTabLayout?.getTabAt(i)
                val text = tab?.text?.toString()
                if (text != null && text != "+") {
                    customTabs.add(text)
                }
            }

            // 保存到SharedPreferences
            val prefs = getSharedPreferences("custom_tabs", MODE_PRIVATE)
            prefs.edit().putString("custom_ai_tabs", customTabs.joinToString(",")).apply()

            Log.d(TAG, "更新自定义标签页顺序: ${customTabs.joinToString(", ")}")

        } catch (e: Exception) {
            Log.e(TAG, "更新自定义标签页顺序失败", e)
        }
    }

    /**
     * 根据当前标签页获取对应的AI列表
     */
    private fun getAIsForCurrentTab(tabPosition: Int, tabName: String?): List<ChatContact> {
        return try {
            when (tabPosition) {
                0 -> {
                    // "全部"标签 - 返回所有已配置的AI
                    allContacts.flatMap { category ->
                        category.contacts.filter { contact ->
                            contact.type == ContactType.AI && contact.isOnline && hasValidApiKey(contact)
                        }
                    }
                }
                1 -> {
                    // "AI助手"标签 - 返回AI助手分组中的AI
                    val aiAssistantCategory = allContacts.find { it.name == "AI助手" }
                    aiAssistantCategory?.contacts?.filter { contact ->
                        contact.type == ContactType.AI && contact.isOnline && hasValidApiKey(contact)
                    } ?: emptyList()
                }
                else -> {
                    // 自定义分组标签 - 返回该分组中的AI
                    if (tabName != null && tabName != "+") {
                        val customCategory = allContacts.find { it.name == tabName }
                        customCategory?.contacts?.filter { contact ->
                            contact.type == ContactType.AI && contact.isOnline && hasValidApiKey(contact)
                        } ?: emptyList()
                    } else {
                        emptyList()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取当前标签页AI列表失败", e)
            emptyList()
        }
    }

    /**
     * 标签拖拽数据类
     */
    private data class TabDragData(val fromPosition: Int, val tabText: String)

    /**
     * 设置标签页拖拽监听器
     */
    private fun setupTabDragListener(tabLayout: com.google.android.material.tabs.TabLayout) {
        try {
            val tabStrip = tabLayout.getChildAt(0) as? ViewGroup ?: return

            for (i in 0 until tabLayout.tabCount - 1) { // 为所有标签设置监听器（除了+号）
                val tabView = tabStrip.getChildAt(i)
                tabView?.setOnDragListener { view, event ->
                    when (event.action) {
                        android.view.DragEvent.ACTION_DRAG_STARTED -> {
                            // 开始拖拽，设置拖拽状态标志
                            isDraggingTabs = true
                            Log.d(TAG, "开始拖拽标签页")
                            true
                        }
                        android.view.DragEvent.ACTION_DRAG_ENTERED -> {
                            // 进入拖拽目标
                            view.alpha = 0.5f
                            true
                        }
                        android.view.DragEvent.ACTION_DRAG_EXITED -> {
                            // 离开拖拽目标
                            view.alpha = 1.0f
                            true
                        }
                        android.view.DragEvent.ACTION_DROP -> {
                            // 放下
                            view.alpha = 1.0f
                            val dragData = event.localState as? TabDragData
                            if (dragData != null) {
                                val targetPosition = i
                                if (targetPosition != dragData.fromPosition) {
                                    moveTab(tabLayout, dragData.fromPosition, targetPosition, dragData.tabText)
                                }
                            }
                            true
                        }
                        android.view.DragEvent.ACTION_DRAG_ENDED -> {
                            // 拖拽结束，清除拖拽状态标志
                            view.alpha = 1.0f
                            // 延迟清除拖拽状态，避免立即触发其他事件
                            Handler(Looper.getMainLooper()).postDelayed({
                                isDraggingTabs = false
                                Log.d(TAG, "拖拽标签页结束")
                            }, 200)
                            true
                        }
                        else -> false
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "设置标签页拖拽监听器失败", e)
        }
    }

    /**
     * 按照保存的顺序加载标签页
     */
    private fun loadTabsInOrder() {
        try {
            val prefs = getSharedPreferences("custom_tabs", MODE_PRIVATE)
            val savedOrder = prefs.getString("all_tab_order", "")

            if (savedOrder.isNullOrEmpty()) {
                // 如果没有保存的顺序，使用默认顺序
                val chatTabLayout = findViewById<com.google.android.material.tabs.TabLayout>(R.id.chat_tab_layout)
                chatTabLayout?.apply {
                    addTab(newTab().setText("全部"))      // 显示所有用户添加的AI助手

                    // 只有在用户未删除AI助手分组时才添加
                    val isAIAssistantGroupDeleted = prefs.getBoolean("ai_assistant_group_deleted", false)
                    if (!isAIAssistantGroupDeleted) {
                        addTab(newTab().setText("AI助手"))    // 预设分组，用户可自定义移动AI到此分组
                    }

                    // 加载已保存的自定义分组标签页
                    loadCustomTabs()
                }
            } else {
                // 按照保存的顺序加载标签
                val tabOrder = savedOrder.split(",")
                val chatTabLayout = findViewById<com.google.android.material.tabs.TabLayout>(R.id.chat_tab_layout)

                val isAIAssistantGroupDeleted = prefs.getBoolean("ai_assistant_group_deleted", false)

                tabOrder.forEach { tabName ->
                    if (tabName.isNotEmpty()) {
                        // 如果是AI助手分组且用户已删除，则跳过
                        if (tabName == "AI助手" && isAIAssistantGroupDeleted) {
                            return@forEach
                        }

                        val newTab = chatTabLayout?.newTab()?.setText(tabName)
                        if (newTab != null) {
                            chatTabLayout.addTab(newTab)
                        }
                    }
                }

                // 如果保存的顺序中没有"全部"和"AI助手"，添加它们
                val hasAll = tabOrder.contains("全部")
                val hasAI = tabOrder.contains("AI助手")

                if (!hasAll) {
                    val allTab = chatTabLayout?.newTab()?.setText("全部")
                    if (allTab != null) {
                        chatTabLayout.addTab(allTab, 0) // 添加到最前面
                    }
                }

                // 只有在用户未删除AI助手分组时才添加
                if (!hasAI && !isAIAssistantGroupDeleted) {
                    val aiTab = chatTabLayout?.newTab()?.setText("AI助手")
                    if (aiTab != null) {
                        val insertPosition = if (hasAll) 1 else 0
                        chatTabLayout.addTab(aiTab, insertPosition)
                    }
                }
            }

            Log.d(TAG, "标签页加载完成，当前顺序: ${getCurrentTabOrder()}")

        } catch (e: Exception) {
            Log.e(TAG, "加载标签页顺序失败", e)
            // 如果加载失败，使用默认顺序
            val chatTabLayout = findViewById<com.google.android.material.tabs.TabLayout>(R.id.chat_tab_layout)
            chatTabLayout?.apply {
                addTab(newTab().setText("全部"))
                addTab(newTab().setText("AI助手"))
                loadCustomTabs()
            }
        }
    }

    /**
     * 获取当前标签页顺序
     */
    private fun getCurrentTabOrder(): String {
        val chatTabLayout = findViewById<com.google.android.material.tabs.TabLayout>(R.id.chat_tab_layout)
        val tabNames = mutableListOf<String>()

        for (i in 0 until (chatTabLayout?.tabCount ?: 0)) {
            val tab = chatTabLayout?.getTabAt(i)
            val text = tab?.text?.toString()
            if (text != null && text != "+") {
                tabNames.add(text)
            }
        }

        return tabNames.joinToString(", ")
    }

    /**
     * 确保所有显示的AI都在allContacts中，以支持长按菜单功能
     */
    private fun ensureAIsInAllContacts(displayCategories: List<ContactCategory>) {
        try {
            displayCategories.forEach { displayCategory ->
                displayCategory.contacts.forEach { contact ->
                    if (contact.type == ContactType.AI) {
                        // 检查这个AI是否已经在allContacts中
                        var found = false
                        for (category in allContacts) {
                            if (category.contacts.any { it.id == contact.id }) {
                                found = true
                                break
                            }
                        }

                        if (!found) {
                            // 如果没找到，添加到对应的分组中
                            val targetGroupName = displayCategory.name
                            val targetCategory = allContacts.find { it.name == targetGroupName }

                            if (targetCategory != null) {
                                // 分组存在，添加AI到该分组
                                val updatedContacts = targetCategory.contacts.toMutableList()
                                updatedContacts.add(contact)
                                val index = allContacts.indexOf(targetCategory)
                                allContacts[index] = targetCategory.copy(contacts = updatedContacts)
                            } else {
                                // 分组不存在，创建新分组
                                val newCategory = ContactCategory(
                                    name = targetGroupName,
                                    contacts = mutableListOf(contact),
                                    isExpanded = true
                                )
                                allContacts.add(newCategory)
                            }

                            Log.d(TAG, "将AI ${contact.name} 添加到allContacts的分组 $targetGroupName")
                        }
                    }
                }
            }

            // 保存更改
            saveContacts()

        } catch (e: Exception) {
            Log.e(TAG, "确保AI在allContacts中失败", e)
        }
    }

    /**
     * 同步AI联系人状态，确保显示的AI与存储的AI状态一致
     */
    private fun syncAIContactStates(displayCategories: List<ContactCategory>) {
        try {
            displayCategories.forEach { displayCategory ->
                val updatedContacts = mutableListOf<ChatContact>()

                displayCategory.contacts.forEach { displayContact ->
                    if (displayContact.type == ContactType.AI) {
                        // 查找在allContacts中对应的AI联系人
                        var storedContact: ChatContact? = null
                        for (category in allContacts) {
                            val found = category.contacts.find { it.id == displayContact.id }
                            if (found != null) {
                                storedContact = found
                                break
                            }
                        }

                        // 如果找到存储的联系人，使用存储的状态
                        if (storedContact != null) {
                            updatedContacts.add(storedContact)
                            Log.d(TAG, "同步AI状态: ${storedContact.name}, 置顶: ${storedContact.isPinned}, 静音: ${storedContact.isMuted}")
                        } else {
                            // 如果没找到，使用显示的联系人
                            updatedContacts.add(displayContact)
                            Log.d(TAG, "使用显示状态: ${displayContact.name}")
                        }
                    } else {
                        // 非AI联系人直接添加
                        updatedContacts.add(displayContact)
                    }
                }

                // 更新显示分类的联系人列表
                val categoryIndex = displayCategories.indexOf(displayCategory)
                if (categoryIndex != -1) {
                    // 这里不能直接修改displayCategories，因为它可能是不可变的
                    // 但我们可以通过适配器更新显示
                    Log.d(TAG, "分类 ${displayCategory.name} 的AI状态已同步")
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "同步AI联系人状态失败", e)
        }
    }

    /**
     * 处理从小组件传入的Intent
     */
    private fun handleWidgetIntent(intent: Intent?) {
        intent?.let {
            try {
                // 检查是否需要直接跳转到设置页面
                val openSettings = it.getBooleanExtra("open_settings", false)
                if (openSettings) {
                    Log.d(TAG, "收到打开设置页面的请求")
                    Handler(Looper.getMainLooper()).post {
                        showSettings()
                    }
                    return
                }

                val source = it.getStringExtra("source")
                if (source?.contains("桌面小组件") == true) {
                    Log.d(TAG, "收到来自桌面小组件的请求: source=$source")

                    val searchQuery = it.getStringExtra("search_query")
                    val searchMode = it.getStringExtra("search_mode")
                    val autoSwitchToAppSearch = it.getBooleanExtra("auto_switch_to_app_search", false)
                    val showInputDialog = it.getBooleanExtra("show_input_dialog", false)

                    // 新增的小组件图标点击参数
                    val autoStartAIChat = it.getBooleanExtra("auto_start_ai_chat", false)
                    val autoStartWebSearch = it.getBooleanExtra("auto_start_web_search", false)
                    val useClipboardIfNoSearchBox = it.getBooleanExtra("use_clipboard_if_no_search_box", false)
                    val showSearchBox = it.getBooleanExtra("show_search_box", true)
                    val defaultAIQuery = it.getStringExtra("default_ai_query")
                    val defaultSearchQuery = it.getStringExtra("default_search_query")
                    val aiEngine = it.getStringExtra("ai_engine")
                    val aiName = it.getStringExtra("ai_name")
                    val searchEngine = it.getStringExtra("search_engine")
                    val searchEngineName = it.getStringExtra("search_engine_name")
                    val appPackage = it.getStringExtra("app_package")
                    val appName = it.getStringExtra("app_name")

                    when {
                        showInputDialog -> {
                            // 显示输入对话框
                            Log.d(TAG, "准备显示小组件输入对话框")
                            // 延迟显示对话框，确保Activity完全加载
                            Handler(Looper.getMainLooper()).postDelayed({
                                showWidgetInputDialog()
                            }, 200)
                        }
                        autoStartAIChat -> {
                            // 自动启动AI对话
                            Log.d(TAG, "自动启动AI对话: $aiName, 显示搜索框: $showSearchBox")
                            Handler(Looper.getMainLooper()).postDelayed({
                                startAIChatFromWidget(aiEngine, aiName, showSearchBox, useClipboardIfNoSearchBox)
                            }, 300)
                        }
                        autoStartWebSearch -> {
                            // 自动启动网络搜索
                            Log.d(TAG, "自动启动网络搜索: $searchEngineName, 显示搜索框: $showSearchBox")
                            Handler(Looper.getMainLooper()).postDelayed({
                                startWebSearchFromWidget(searchEngine, searchEngineName, showSearchBox, useClipboardIfNoSearchBox)
                            }, 300)
                        }
                        autoSwitchToAppSearch -> {
                            // 自动切换到应用搜索页面
                            Log.d(TAG, "自动切换到应用搜索: $appName, 显示搜索框: $showSearchBox")
                            Handler(Looper.getMainLooper()).postDelayed({
                                startAppSearchFromWidget(appPackage, appName, showSearchBox, useClipboardIfNoSearchBox)
                            }, 300)
                        }
                        searchMode == "ai_chat" -> {
                            // 处理AI对话模式（这种情况下应该已经启动了ChatActivity）
                            Log.d(TAG, "AI对话模式，query: $searchQuery")
                        }
                        searchMode == "app_search" -> {
                            // 切换到应用搜索页面
                            switchToAppSearchWithQuery(searchQuery)
                        }
                        searchMode == "web_search" -> {
                            // 网络搜索模式（这种情况下应该已经启动了DualFloatingWebViewService）
                            Log.d(TAG, "网络搜索模式，query: $searchQuery")
                        }
                        searchQuery != null -> {
                            // 有搜索查询但没有指定模式，显示选择对话框
                            showSearchModeSelectionDialog(searchQuery)
                        }
                        else -> {
                            // 默认情况，记录日志
                            Log.d(TAG, "收到小组件请求但没有匹配的处理逻辑")
                        }
                    }
                } else {
                    // 不是来自小组件的请求，记录日志
                    Log.d(TAG, "收到非小组件请求: source=$source")
                }
            } catch (e: Exception) {
                Log.e(TAG, "处理小组件Intent失败", e)
            }
        }
    }

    /**
     * 显示小组件输入对话框
     */
    private fun showWidgetInputDialog() {
        Log.d(TAG, "显示小组件输入对话框")

        val input = EditText(this).apply {
            hint = "请输入搜索内容"
            setPadding(50, 30, 50, 30)
            // 自动获取焦点并显示键盘
            requestFocus()
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("搜索内容")
            .setMessage("请输入您要搜索的内容：")
            .setView(input)
            .setPositiveButton("AI对话") { _, _ ->
                val query = input.text.toString().trim()
                if (query.isNotEmpty()) {
                    startAIChatWithQuery(query)
                } else {
                    Toast.makeText(this, "请输入搜索内容", Toast.LENGTH_SHORT).show()
                }
            }
            .setNeutralButton("应用搜索") { _, _ ->
                val query = input.text.toString().trim()
                if (query.isNotEmpty()) {
                    switchToAppSearchWithQuery(query)
                } else {
                    Toast.makeText(this, "请输入搜索内容", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("网络搜索") { _, _ ->
                val query = input.text.toString().trim()
                if (query.isNotEmpty()) {
                    startWebSearchWithQuery(query)
                } else {
                    Toast.makeText(this, "请输入搜索内容", Toast.LENGTH_SHORT).show()
                }
            }
            .setCancelable(true)
            .create()

        dialog.show()

        // 延迟显示键盘，确保对话框完全显示后再显示键盘
        Handler(Looper.getMainLooper()).postDelayed({
            input.requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }, 100)
    }

    /**
     * 显示搜索模式选择对话框
     */
    private fun showSearchModeSelectionDialog(query: String) {
        val options = arrayOf("AI对话", "应用搜索", "网络搜索")

        AlertDialog.Builder(this)
            .setTitle("选择搜索方式")
            .setMessage("搜索内容：$query")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> startAIChatWithQuery(query)
                    1 -> switchToAppSearchWithQuery(query)
                    2 -> startWebSearchWithQuery(query)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 启动AI对话
     */
    private fun startAIChatWithQuery(query: String?) {
        try {
            // 找到第一个可用的AI联系人
            val aiContact = findFirstAvailableAIContact()
            if (aiContact != null) {
                if (query != null) {
                    openChatWithContactAndMessage(aiContact, query)
                } else {
                    // 只跳转到对话界面，不发送消息，激活输入状态
                    openChatWithContactOnly(aiContact)
                }
            } else {
                // 没有AI联系人，提示用户添加
                showAddAIContactDialog(query ?: "")
            }
        } catch (e: Exception) {
            Log.e(TAG, "启动AI对话失败", e)
            Toast.makeText(this, "启动AI对话失败", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 切换到应用搜索页面并设置查询
     */
    private fun switchToAppSearchWithQuery(query: String?) {
        try {
            // 切换到应用搜索页面
            currentState = UIState.APP_SEARCH
            showAppSearch()

            // 如果有查询内容，设置到输入框
            query?.let {
                appSearchInput.setText(it)
                appSearchInput.setSelection(it.length)
                // 更新搜索查询
                if (::appSearchAdapter.isInitialized) {
                    appSearchAdapter.updateSearchQuery(it)
                }
                // 更新提示文本
                appSearchHint.text = "输入关键词：$it，点击应用图标进行搜索"
            }
        } catch (e: Exception) {
            Log.e(TAG, "切换到应用搜索失败", e)
            Toast.makeText(this, "切换到应用搜索失败", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 启动网络搜索
     */
    private fun startWebSearchWithQuery(query: String) {
        try {
            val intent = Intent(this, DualFloatingWebViewService::class.java).apply {
                putExtra("search_query", query)
                putExtra("engine_key", "baidu")
                putExtra("search_source", "简易模式小组件")
            }
            startService(intent)
            Toast.makeText(this, "正在启动网络搜索...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "启动网络搜索失败", e)
            Toast.makeText(this, "启动网络搜索失败", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 找到第一个可用的AI联系人
     */
    private fun findFirstAvailableAIContact(): ChatContact? {
        return try {
            allContacts.flatMap { it.contacts }
                .firstOrNull { contact ->
                    isAIContact(contact) && hasValidApiKey(contact)
                }
        } catch (e: Exception) {
            Log.e(TAG, "查找AI联系人失败", e)
            null
        }
    }

    /**
     * 显示添加AI联系人对话框（带查询参数）
     */
    private fun showAddAIContactDialog(query: String) {
        AlertDialog.Builder(this)
            .setTitle("需要添加AI助手")
            .setMessage("要使用AI对话功能，请先添加AI助手。\n\n搜索内容：$query")
            .setPositiveButton("添加AI助手") { _, _ ->
                openAddAIContactDialog()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 只打开AI对话界面，不发送消息，激活输入状态
     */
    private fun openChatWithContactOnly(contact: ChatContact) {
        try {
            val intent = Intent(this, ChatActivity::class.java).apply {
                putExtra(ChatActivity.EXTRA_CONTACT, contact)
                putExtra("activate_input_only", true) // 只激活输入状态
                putExtra("source", "桌面小组件")
            }
            startActivity(intent)
            Log.d(TAG, "打开AI对话界面: ${contact.name}, 只激活输入状态")
        } catch (e: Exception) {
            Log.e(TAG, "打开AI对话界面失败", e)
            Toast.makeText(this, "打开对话界面失败", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 从小组件启动AI对话
     */
    private fun startAIChatFromWidget(aiEngine: String?, aiName: String?, showSearchBox: Boolean, useClipboard: Boolean) {
        Log.d(TAG, "从小组件启动AI对话: $aiName, 显示搜索框: $showSearchBox, 使用剪贴板: $useClipboard")

        // 确定要使用的查询内容和是否自动发送
        val (queryToUse, shouldAutoSend) = if (!showSearchBox && useClipboard) {
                // 没有搜索框时，尝试使用剪贴板内容
                val clipboardText = com.example.dalao.widget.ClipboardHelper.getClipboardText(this)
                if (com.example.dalao.widget.ClipboardHelper.isValidSearchQuery(clipboardText)) {
                    val cleanedText = com.example.dalao.widget.ClipboardHelper.cleanTextForSearch(clipboardText)
                    Log.d(TAG, "使用剪贴板内容作为AI查询: $cleanedText")
                    Pair(cleanedText, true) // 有剪贴板内容时自动发送
                } else {
                    Log.d(TAG, "剪贴板内容无效或为空，只跳转到对话界面")
                    Pair(null, false) // 无有效剪贴板内容时不自动发送
                }
            } else {
                // 有搜索框时也不自动发送消息，只激活输入状态
                Pair(null, false)
            }

        try {
            if (aiEngine.isNullOrEmpty() || aiName.isNullOrEmpty()) {
                Log.w(TAG, "AI引擎信息不完整，使用默认查询")
                if (shouldAutoSend && queryToUse != null) {
                    startAIChatWithQuery(queryToUse)
                } else {
                    // 只跳转到对话界面，不发送消息
                    startAIChatWithQuery(null)
                }
                return
            }

            // 创建AI联系人，包含必要的API配置
            val apiKey = getApiKeyForAI(aiName)

            // 检查API密钥是否配置
            if (apiKey.isBlank()) {
                Log.w(TAG, "AI助手 $aiName 的API密钥未配置")
                // 显示配置提示
                Handler(Looper.getMainLooper()).post {
                    showAIConfigurationDialog(aiName, queryToUse ?: "")
                }
                return
            }

            val aiContact = ChatContact(
                id = "widget_$aiEngine",
                name = aiName,
                type = ContactType.AI,
                lastMessage = "",
                lastMessageTime = System.currentTimeMillis(),
                customData = mutableMapOf(
                    "engine" to aiEngine,
                    "api_url" to getDefaultApiUrl(aiName),
                    "api_key" to apiKey,
                    "model" to getDefaultModel(aiName)
                ),
                aiMembers = emptyList()
            )

            // 启动ChatActivity
            val intent = Intent(this, ChatActivity::class.java).apply {
                putExtra(ChatActivity.EXTRA_CONTACT, aiContact)
                if (shouldAutoSend && queryToUse != null) {
                    putExtra("auto_send_message", queryToUse)
                    Log.d(TAG, "将自动发送消息: $queryToUse")
                } else {
                    // 不自动发送消息，只激活输入状态
                    putExtra("activate_input_only", true)
                    Log.d(TAG, "只激活输入状态，不自动发送消息")
                }
                putExtra("source", "桌面小组件")
            }

            startActivity(intent)
            Log.d(TAG, "AI对话启动成功: $aiName, 自动发送: $shouldAutoSend")

        } catch (e: Exception) {
            Log.e(TAG, "从小组件启动AI对话失败", e)
            // 回退到简单的AI对话启动
            if (shouldAutoSend && queryToUse != null) {
                startAIChatWithQuery(queryToUse)
            } else {
                startAIChatWithQuery(null) // 只跳转，不发送消息
            }
        }
    }

    /**
     * 从小组件启动网络搜索
     */
    private fun startWebSearchFromWidget(searchEngine: String?, searchEngineName: String?, showSearchBox: Boolean, useClipboard: Boolean) {
        try {
            Log.d(TAG, "从小组件启动网络搜索: $searchEngineName, 显示搜索框: $showSearchBox, 使用剪贴板: $useClipboard")

            // 确定要使用的搜索内容 - 始终尝试使用剪贴板内容
            val clipboardText = com.example.dalao.widget.ClipboardHelper.getClipboardText(this)
            val queryToUse = if (com.example.dalao.widget.ClipboardHelper.isValidSearchQuery(clipboardText)) {
                val cleanedText = com.example.dalao.widget.ClipboardHelper.cleanTextForSearch(clipboardText)
                Log.d(TAG, "使用剪贴板内容进行网络搜索: $cleanedText")
                cleanedText
            } else {
                Log.d(TAG, "剪贴板内容无效或为空，直接打开搜索引擎首页")
                null // 返回null表示直接打开首页
            }

            // 切换到浏览器界面
            showBrowser()

            // 根据搜索引擎参数设置正确的搜索引擎
            setCurrentSearchEngineByName(searchEngine, searchEngineName)

            if (queryToUse != null) {
                // 有搜索内容时执行搜索
                browserSearchInput.setText(queryToUse)
                performBrowserSearch()
                Log.d(TAG, "网络搜索启动成功: $queryToUse")
            } else {
                // 没有搜索内容时，根据搜索引擎打开首页
                val homepageUrl = getSearchEngineHomepage(searchEngine, searchEngineName)
                if (homepageUrl != null) {
                    // 直接加载首页URL
                    loadBrowserContent(homepageUrl)
                    Log.d(TAG, "打开搜索引擎首页: $homepageUrl")
                } else {
                    // 如果无法确定首页，直接打开浏览器空白页
                    showBrowser()
                    Log.d(TAG, "打开浏览器空白页")
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "从小组件启动网络搜索失败", e)
            // 回退：直接打开浏览器空白页
            showBrowser()
            Log.d(TAG, "回退：打开浏览器空白页")
        }
    }

    /**
     * 从小组件启动应用搜索
     */
    private fun startAppSearchFromWidget(appPackage: String?, appName: String?, showSearchBox: Boolean, useClipboard: Boolean) {
        try {
            Log.d(TAG, "从小组件启动应用搜索: $appName, 显示搜索框: $showSearchBox, 使用剪贴板: $useClipboard")

            // 确定要使用的搜索内容 - 始终尝试使用剪贴板内容
            val clipboardText = com.example.dalao.widget.ClipboardHelper.getClipboardText(this)
            val queryToUse = if (com.example.dalao.widget.ClipboardHelper.isValidSearchQuery(clipboardText)) {
                val cleanedText = com.example.dalao.widget.ClipboardHelper.cleanTextForSearch(clipboardText)
                Log.d(TAG, "使用剪贴板内容进行应用搜索: $cleanedText")
                cleanedText
            } else {
                Log.d(TAG, "剪贴板内容无效或为空，直接打开应用")
                null // 返回null表示直接打开应用
            }

            if (queryToUse != null && !appPackage.isNullOrEmpty()) {
                // 有搜索内容时，尝试直接在目标应用中搜索
                val success = launchAppWithSearch(appPackage, appName, queryToUse)
                if (!success) {
                    // 如果直接搜索失败，尝试直接打开应用
                    Log.w(TAG, "直接应用搜索失败，尝试直接打开应用")
                    val launchIntent = packageManager.getLaunchIntentForPackage(appPackage)
                    if (launchIntent != null) {
                        startActivity(launchIntent)
                        Log.d(TAG, "直接启动应用: $appName")
                    } else {
                        Log.w(TAG, "无法启动应用: $appName, 应用可能未安装")
                        Toast.makeText(this, "应用 $appName 未安装", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                // 没有搜索内容时，直接打开应用
                if (!appPackage.isNullOrEmpty()) {
                    val launchIntent = packageManager.getLaunchIntentForPackage(appPackage)
                    if (launchIntent != null) {
                        startActivity(launchIntent)
                        Log.d(TAG, "直接启动应用: $appName")
                    } else {
                        Log.w(TAG, "无法启动应用: $appName, 应用可能未安装")
                        Toast.makeText(this, "应用 $appName 未安装", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Log.w(TAG, "应用包名为空，无法启动应用")
                    Toast.makeText(this, "应用信息不完整", Toast.LENGTH_SHORT).show()
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "从小组件启动应用搜索失败", e)
            // 回退：尝试直接打开应用
            if (!appPackage.isNullOrEmpty()) {
                try {
                    val launchIntent = packageManager.getLaunchIntentForPackage(appPackage)
                    if (launchIntent != null) {
                        startActivity(launchIntent)
                        Log.d(TAG, "回退启动应用成功: $appName")
                    } else {
                        Toast.makeText(this, "应用 $appName 未安装", Toast.LENGTH_SHORT).show()
                    }
                } catch (e2: Exception) {
                    Log.e(TAG, "回退启动应用也失败", e2)
                    Toast.makeText(this, "启动应用失败", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "应用信息不完整", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 在指定应用中启动搜索
     */
    private fun launchAppWithSearch(packageName: String, appName: String?, query: String): Boolean {
        try {
            Log.d(TAG, "尝试在应用 $appName 中搜索: $query")

            // 根据不同应用使用不同的搜索Intent
            val searchIntent = when (packageName) {
                "com.tencent.mm" -> {
                    // 微信搜索
                    Intent().apply {
                        action = Intent.ACTION_VIEW
                        data = android.net.Uri.parse("weixin://")
                        setPackage(packageName)
                    }
                }
                "com.taobao.taobao" -> {
                    // 淘宝搜索
                    Intent().apply {
                        action = Intent.ACTION_VIEW
                        data = android.net.Uri.parse("taobao://s.taobao.com/search?q=${android.net.Uri.encode(query)}")
                        setPackage(packageName)
                    }
                }
                "com.jingdong.app.mall" -> {
                    // 京东搜索
                    Intent().apply {
                        action = Intent.ACTION_VIEW
                        data = android.net.Uri.parse("openjd://virtual?params={\"des\":\"productList\",\"keyWord\":\"${query}\"}")
                        setPackage(packageName)
                    }
                }
                "com.ss.android.ugc.aweme" -> {
                    // 抖音搜索
                    Intent().apply {
                        action = Intent.ACTION_VIEW
                        data = android.net.Uri.parse("snssdk1128://search/tabs?keyword=${android.net.Uri.encode(query)}")
                        setPackage(packageName)
                    }
                }
                "com.xingin.xhs" -> {
                    // 小红书搜索
                    Intent().apply {
                        action = Intent.ACTION_VIEW
                        data = android.net.Uri.parse("xhsdiscover://search/result?keyword=${android.net.Uri.encode(query)}")
                        setPackage(packageName)
                    }
                }
                "com.sankuai.meituan" -> {
                    // 美团搜索
                    Intent().apply {
                        action = Intent.ACTION_VIEW
                        data = android.net.Uri.parse("imeituan://www.meituan.com/search?q=${android.net.Uri.encode(query)}")
                        setPackage(packageName)
                    }
                }
                "me.ele" -> {
                    // 饿了么搜索
                    Intent().apply {
                        action = Intent.ACTION_VIEW
                        data = android.net.Uri.parse("eleme://search?keyword=${android.net.Uri.encode(query)}")
                        setPackage(packageName)
                    }
                }
                "com.android.chrome" -> {
                    // Chrome浏览器搜索
                    Intent().apply {
                        action = Intent.ACTION_VIEW
                        data = android.net.Uri.parse("googlechrome://www.google.com/search?q=${android.net.Uri.encode(query)}")
                        setPackage(packageName)
                    }
                }
                else -> {
                    // 通用搜索Intent，尝试使用ACTION_WEB_SEARCH
                    Intent().apply {
                        action = Intent.ACTION_WEB_SEARCH
                        putExtra("query", query)
                        setPackage(packageName)
                    }
                }
            }

            // 尝试启动搜索Intent
            startActivity(searchIntent)
            Log.d(TAG, "成功在应用 $appName 中启动搜索: $query")
            Toast.makeText(this, "在 $appName 中搜索: $query", Toast.LENGTH_SHORT).show()
            return true

        } catch (e: Exception) {
            Log.e(TAG, "在应用 $appName 中搜索失败", e)
            return false
        }
    }

    /**
     * 根据搜索引擎名称设置当前搜索引擎
     */
    private fun setCurrentSearchEngineByName(searchEngine: String?, searchEngineName: String?) {
        try {
            Log.d(TAG, "设置搜索引擎: searchEngine='$searchEngine', searchEngineName='$searchEngineName'")

            // 获取所有可用的搜索引擎
            val allEngines = com.example.aifloatingball.model.SearchEngine.DEFAULT_ENGINES

            // 根据搜索引擎参数查找匹配的搜索引擎
            val targetEngine = allEngines.find { engine ->
                when {
                    // 优先匹配 searchEngine 参数
                    !searchEngine.isNullOrEmpty() -> {
                        engine.name.equals(searchEngine, ignoreCase = true) ||
                        engine.displayName.equals(searchEngine, ignoreCase = true)
                    }
                    // 其次匹配 searchEngineName 参数
                    !searchEngineName.isNullOrEmpty() -> {
                        engine.name.equals(searchEngineName, ignoreCase = true) ||
                        engine.displayName.equals(searchEngineName, ignoreCase = true)
                    }
                    else -> false
                }
            }

            if (targetEngine != null) {
                currentSearchEngine = targetEngine
                Log.d(TAG, "成功设置搜索引擎: ${targetEngine.displayName} (${targetEngine.name})")
            } else {
                Log.w(TAG, "未找到匹配的搜索引擎，使用默认搜索引擎")
                // 保持当前搜索引擎不变，或使用默认的Google
                if (currentSearchEngine == null) {
                    currentSearchEngine = allEngines.find { it.name == "google" } ?: allEngines.firstOrNull()
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "设置搜索引擎失败", e)
        }
    }

    /**
     * 获取搜索引擎首页URL
     */
    private fun getSearchEngineHomepage(searchEngine: String?, searchEngineName: String?): String? {
        Log.d(TAG, "获取搜索引擎首页: searchEngine='$searchEngine', searchEngineName='$searchEngineName'")

        return when (searchEngine?.lowercase()) {
            "baidu", "百度" -> "https://www.baidu.com"
            "google", "谷歌" -> "https://www.google.com"
            "bing", "必应" -> "https://www.bing.com"
            "sogou", "搜狗" -> "https://www.sogou.com"
            "360", "360搜索", "so360" -> "https://www.so.com"
            "yandex" -> "https://www.yandex.com"
            "duckduckgo" -> "https://duckduckgo.com"
            "quark" -> "https://quark.sm.cn"
            else -> {
                // 根据搜索引擎名称推测
                when (searchEngineName?.lowercase()) {
                    "百度" -> "https://www.baidu.com"
                    "google", "谷歌" -> "https://www.google.com"
                    "bing", "必应" -> "https://www.bing.com"
                    "搜狗" -> "https://www.sogou.com"
                    "360搜索" -> "https://www.so.com"
                    "夸克" -> "https://quark.sm.cn"
                    "duckduckgo" -> "https://duckduckgo.com"
                    else -> {
                        Log.w(TAG, "未知的搜索引擎: $searchEngine / $searchEngineName")
                        null
                    }
                }
            }
        }
    }

    /**
     * 显示AI配置对话框
     */
    private fun showAIConfigurationDialog(aiName: String, queryToUse: String) {
        AlertDialog.Builder(this)
            .setTitle("配置AI助手")
            .setMessage("要使用 $aiName，请先配置API密钥。\n\n搜索内容：$queryToUse")
            .setPositiveButton("去配置") { _, _ ->
                // 打开AI联系人列表界面进行配置
                openAIContactListActivity()
            }
            .setNegativeButton("使用默认AI") { _, _ ->
                // 使用默认的AI对话启动
                startAIChatWithQuery(queryToUse)
            }
            .setNeutralButton("取消", null)
            .show()
    }

    // ==================== 横滑预览指示器相关方法 ====================

    private var swipePreviewIndicator: View? = null
    private var swipePreviewContainer: LinearLayout? = null
    private var swipePreviewTitle: TextView? = null
    private var swipePreviewDots: MutableList<View> = mutableListOf()

    // 底部tab横滑手势相关
    private var tabSwipeGestureIndicator: View? = null
    private var lastSwipeTime = 0L
    private val swipeDebounceDelay = 300L // 防抖延迟300ms

    // 搜索tab横滑热区相关
    private var searchTabSwipeHotArea: View? = null
    private var searchTabSwipeIndicator: View? = null
    private var lastSearchTabSwipeTime = 0L

    // Tab区域滑动相关
    private var tabSwipeIndicator: View? = null
    private var lastTabSwipeTime = 0L
    private val tabSwipeDebounceDelay = 300L

    // 对话tab横滑手势相关
    private var chatTabSwipeGestureDetector: GestureDetectorCompat? = null
    private var chatTabSwipeIndicator: View? = null
    private var lastChatTabSwipeTime = 0L

    /**
     * 显示横滑预览指示器
     */
    private fun showSwipePreviewIndicator(cards: List<GestureCardWebViewManager.WebViewCardData>, currentIndex: Int) {
        if (swipePreviewIndicator == null) {
            createSwipePreviewIndicator()
        }

        // 更新指示器内容
        updateSwipePreviewDots(cards.size, currentIndex)

        // 更新当前页面标题（显示前两个字）
        if (currentIndex < cards.size) {
            val title = cards[currentIndex].title
            val shortTitle = if (title.length >= 2) title.substring(0, 2) else title
            swipePreviewTitle?.text = shortTitle
        }

        // 显示指示器 - 简化动画效果
        swipePreviewIndicator?.apply {
            visibility = View.VISIBLE
            alpha = 0f

            animate()
                .alpha(1f)
                .setDuration(150) // 减少动画时间
                .start()
        }
    }

    /**
     * 创建横滑预览指示器
     */
    private fun createSwipePreviewIndicator() {
        // 创建主容器 - 垂直布局，包含指示器和标题
        val mainContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setPadding(16, 8, 16, 8)
        }

        // 创建圆点指示器容器
        swipePreviewContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 4) // 圆点下方留空间给标题
        }

        // 创建标题显示
        val titleText = TextView(this).apply {
            textSize = 12f
            setTextColor(android.graphics.Color.WHITE)
            gravity = android.view.Gravity.CENTER
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }

        // 添加到主容器
        mainContainer.addView(swipePreviewContainer)
        mainContainer.addView(titleText)

        // 创建指示器背景 - 去掉透明区域，只保留指示器
        swipePreviewIndicator = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
                bottomMargin = 100 // 距离底部100dp，避免遮挡tab区域
            }

            // 去掉背景，只保留内容
            background = null
            elevation = 0f

            addView(mainContainer)
            visibility = View.GONE
        }

        // 保存标题引用
        swipePreviewTitle = titleText

        // 添加到主布局
        browserLayout.addView(swipePreviewIndicator)
    }

    /**
     * 更新横滑预览指示器的点
     */
    private fun updateSwipePreviewDots(cardCount: Int, currentIndex: Int) {
        swipePreviewContainer?.removeAllViews()
        swipePreviewDots.clear()

        for (i in 0 until cardCount) {
            val dot = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(12, 12).apply { // 减小圆点尺寸
                    setMargins(4, 0, 4, 0)
                }
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    if (i == currentIndex) {
                        setColor(android.graphics.Color.WHITE) // 当前页面白色
                    } else {
                        setColor(android.graphics.Color.parseColor("#80FFFFFF")) // 其他页面半透明白色
                    }
                }
            }
            swipePreviewDots.add(dot)
            swipePreviewContainer?.addView(dot)
        }
    }

    /**
     * 更新横滑预览指示器位置
     */
    private fun updateSwipePreviewIndicator(position: Int, positionOffset: Float) {
        // 更新当前活跃的点
        swipePreviewDots.forEachIndexed { index, dot ->
            val alpha = when {
                index == position -> 1f - positionOffset
                index == position + 1 -> positionOffset
                else -> 0.5f
            }
            dot.alpha = alpha
        }

        // 更新标题显示
        val cards = gestureCardWebViewManager?.getAllCards() ?: return
        val currentIndex = if (positionOffset > 0.5f && position + 1 < cards.size) position + 1 else position
        if (currentIndex < cards.size) {
            val title = cards[currentIndex].title
            val shortTitle = if (title.length >= 2) title.substring(0, 2) else title
            swipePreviewTitle?.text = shortTitle
        }
    }

    /**
     * 隐藏横滑预览指示器
     */
    private fun hideSwipePreviewIndicator() {
        swipePreviewIndicator?.animate()
            ?.alpha(0f)
            ?.setDuration(150) // 减少动画时间
            ?.withEndAction {
                swipePreviewIndicator?.visibility = View.GONE
            }
            ?.start()
    }

    // ==================== 底部tab区域横滑手势处理 ====================

    /**
     * 设置底部tab区域的横滑手势 - 专门用于切换webview卡片
     * 注意：现在手势检测已经集成到setupBottomNavigation中的每个tab
     */
    private fun setupTabAreaSwipeGesture() {
        Log.d(TAG, "✅ 底部tab区域webview卡片切换手势已集成到setupBottomNavigation中")

        // 添加长按测试功能到整个底部导航栏
        val bottomNav = findViewById<LinearLayout>(R.id.bottom_navigation)
        bottomNav?.setOnLongClickListener {
            if (getCurrentTabIndex() == 1) {
                // 调试：测试新的手势机制
                testNewGestureMechanism()
            } else {
                Toast.makeText(this@SimpleModeActivity, "请先切换到搜索tab再测试", Toast.LENGTH_SHORT).show()
            }
            true
        }
    }

    /**
     * 创建webview卡片切换手势检测器
     */
    private fun createWebViewCardSwipeDetector(): GestureDetector {
        return GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false

                // 防抖处理
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastSwipeTime < swipeDebounceDelay) {
                    Log.d(TAG, "🚫 手势防抖，忽略此次操作")
                    return false
                }

                val deltaX = e2.x - e1.x
                val deltaY = e2.y - e1.y

                // 确保是水平滑动且滑动距离足够
                if (abs(deltaX) > abs(deltaY) && abs(deltaX) > 80) { // 降低到80px
                    lastSwipeTime = currentTime

                    Log.d(TAG, "🎯 底部tab检测到横滑手势，deltaX: $deltaX, 当前tab: ${getCurrentTabIndex()}")

                    // 根据当前tab执行不同的切换逻辑
                    when (getCurrentTabIndex()) {
                        0 -> { // 0 = CHAT (对话tab)
                            if (deltaX > 0) {
                                // 向右滑动，切换到下一个对话标签（右边的标签）
                                val targetTabName = switchToNextChatTab()
                                showChatTabSwipeIndicator(targetTabName ?: "下一个标签")
                                Log.d(TAG, "✅ 底部tab右滑成功 - 切换到下一个对话标签: $targetTabName")
                            } else {
                                // 向左滑动，切换到上一个对话标签（左边的标签）
                                val targetTabName = switchToPreviousChatTab()
                                showChatTabSwipeIndicator(targetTabName ?: "上一个标签")
                                Log.d(TAG, "✅ 底部tab左滑成功 - 切换到上一个对话标签: $targetTabName")
                            }
                            return true
                        }
                        1 -> { // 1 = BROWSER (搜索tab)
                            // 检查是否有活动的WebView
                            val hasActiveWebView = gestureCardWebViewManager?.getCurrentCard()?.webView != null
                            if (hasActiveWebView) {
                                if (deltaX > 0) {
                                    // 向右滑动，网页后退
                                    goBackInCurrentWebView()
                                    showSearchTabSwipeIndicator("后退")
                                    Log.d(TAG, "✅ 底部tab右滑成功 - 网页后退")
                                } else {
                                    // 向左滑动，网页前进
                                    goForwardInCurrentWebView()
                                    showSearchTabSwipeIndicator("前进")
                                    Log.d(TAG, "✅ 底部tab左滑成功 - 网页前进")
                                }
                                return true
                            } else {
                                // 没有活动的WebView，消费事件防止系统处理
                                Log.d(TAG, "⚠️ 搜索tab没有活动的WebView，消费侧滑手势防止系统处理")
                                return true
                            }
                        }
                        else -> {
                            // 其他tab暂不支持横滑功能
                            Log.d(TAG, "⚠️ 当前tab不支持横滑功能，当前tab: ${getCurrentTabIndex()}")
                        }
                    }
                } else {
                    Log.d(TAG, "🚫 手势不符合条件 - deltaX: $deltaX, deltaY: $deltaY")
                }
                return false
            }

            override fun onDown(e: MotionEvent): Boolean {
                Log.d(TAG, "👆 底部tab区域按下，当前tab: ${getCurrentTabIndex()}")
                return true
            }
        })
    }

    /**
     * 设置Material波浪追踪器和层叠卡片预览器
     */
    private fun setupMaterialWaveTracker() {
        try {
            // 创建层叠卡片预览器（新的主要预览方式）
            stackedCardPreview = com.example.aifloatingball.views.StackedCardPreview(this).apply {
                // 设置层级
                elevation = 18f

                // 设置底部导航栏高度提供者
                setBottomNavHeightProvider {
                    val bottomNav = findViewById<LinearLayout>(R.id.bottom_navigation)
                    if (bottomNav != null && bottomNav.height > 0) {
                        bottomNav.height
                    } else {
                        // 使用布局中定义的64dp高度作为fallback
                        (64 * resources.displayMetrics.density).toInt()
                    }
                }

                // 设置卡片选择监听器
                setOnCardSelectedListener { cardIndex ->
                    // 切换到选中的卡片
                    switchToWebViewCard(cardIndex)
                }

                // 设置卡片关闭监听器
                setOnCardCloseListener { cardIndex ->
                    // 关闭指定的webview卡片
                    closeWebViewCard(cardIndex)
                }

                // 设置卡片刷新监听器
                setOnCardRefreshListener { cardIndex ->
                    // 刷新指定的webview卡片
                    refreshWebViewCard(cardIndex)
                }

                // 设置所有卡片移除监听器
                setOnAllCardsRemovedListener {
                    // 所有卡片都被移除了，返回搜索tab
                    browserLayout.postDelayed({
                        switchToSearchTab()
                        Log.d(TAG, "所有卡片已移除，返回搜索tab")
                    }, 300)
                }

                // 设置新建卡片请求监听器
                setOnNewCardRequestedListener {
                    // 显示新建卡片选择弹窗
                    showNewCardSelectionDialog()
                }
            }

            // 保留原有的Material波浪追踪器作为备用
            materialWaveTracker = com.example.aifloatingball.views.MaterialWaveTracker(this).apply {
                // 设置卡片颜色
                setCardColor(android.graphics.Color.WHITE)

                // 设置层级
                elevation = 16f

                // 设置底部导航栏高度提供者
                setBottomNavHeightProvider {
                    val bottomNav = findViewById<LinearLayout>(R.id.bottom_navigation)
                    if (bottomNav != null && bottomNav.height > 0) {
                        bottomNav.height
                    } else {
                        // 使用布局中定义的64dp高度作为fallback
                        (64 * resources.displayMetrics.density).toInt()
                    }
                }

                // 设置卡片选择监听器
                setOnCardSelectedListener { cardIndex ->
                    // 切换到选中的卡片
                    switchToWebViewCard(cardIndex)
                }
            }

            // 将预览器添加为独立的覆盖层，不影响底部导航栏
            // 获取Activity的根视图（DecorView），这样不会影响布局
            val decorView = window.decorView as ViewGroup
            val bottomNav = findViewById<LinearLayout>(R.id.bottom_navigation)

            if (decorView != null && bottomNav != null) {
                // 添加层叠卡片预览器（主要预览方式）
                stackedCardPreview?.apply {
                    // 设置为全屏覆盖，但不影响布局
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )

                    // 初始状态设置为不可交互，激活时会重新设置
                    isClickable = false
                    isFocusable = false
                    isFocusableInTouchMode = false
                    isEnabled = false
                    // 初始状态隐藏
                    visibility = View.GONE

                    // 不设置OnTouchListener，让StackedCardPreview自己处理触摸事件
                }

                // 将Material波浪追踪器添加到DecorView作为覆盖层（备用）
                materialWaveTracker?.apply {
                    // 设置为全屏覆盖，但不影响布局
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )

                    // 设置为完全不可交互，让触摸事件穿透到底层
                    isClickable = false
                    isFocusable = false
                    isFocusableInTouchMode = false
                    isEnabled = false
                    // 初始状态隐藏
                    visibility = View.GONE

                    // 确保不拦截触摸事件
                    setOnTouchListener { _, _ -> false }
                }

                // 添加到DecorView作为最顶层的覆盖层，不影响原有布局
                decorView.addView(stackedCardPreview)
                decorView.addView(materialWaveTracker)

                // 初始化卡片数据
                updateWaveTrackerCards()

                Log.d(TAG, "✅ 卡片预览波浪追踪器已成功设置")
            } else {
                Log.e(TAG, "❌ 无法找到底部导航栏或根布局")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 设置Material波浪追踪器失败", e)
        }
    }

    /**
     * 更新卡片预览器的卡片数据
     */
    private fun updateWaveTrackerCards() {
        try {
            // 合并所有管理器的卡片
            val gestureCards = gestureCardWebViewManager?.getAllCards() ?: emptyList()
            val mobileCards = mobileCardManager?.getAllCards() ?: emptyList()
            val allCards = mutableListOf<GestureCardWebViewManager.WebViewCardData>()

            allCards.addAll(gestureCards)
            allCards.addAll(mobileCards)

            Log.d(TAG, "更新卡片预览器 - 手势卡片: ${gestureCards.size}, 手机卡片: ${mobileCards.size}, 总计: ${allCards.size}")

            if (allCards.isNotEmpty()) {
                // 为MaterialWaveTracker准备数据
                val waveTrackerCardDataList = allCards.map { cardData ->
                    com.example.aifloatingball.views.MaterialWaveTracker.WebViewCardData(
                        title = cardData.title ?: "无标题",
                        url = cardData.url ?: "",
                        favicon = null, // 可以后续添加favicon支持
                        screenshot = cardData.webView?.let { webView ->
                            // 尝试获取WebView截图
                            try {
                                val bitmap = Bitmap.createBitmap(
                                    webView.width,
                                    webView.height,
                                    Bitmap.Config.ARGB_8888
                                )
                                val canvas = Canvas(bitmap)
                                webView.draw(canvas)
                                bitmap
                            } catch (e: Exception) {
                                Log.w(TAG, "获取WebView截图失败", e)
                                null
                            }
                        }
                    )
                }

                // 为StackedCardPreview准备数据
                val stackedCardDataList = allCards.map { cardData ->
                    com.example.aifloatingball.views.StackedCardPreview.WebViewCardData(
                        title = cardData.title ?: "无标题",
                        url = cardData.url ?: "",
                        favicon = null, // 可以后续添加favicon支持
                        screenshot = cardData.webView?.let { webView ->
                            // 尝试获取WebView截图
                            try {
                                val bitmap = Bitmap.createBitmap(
                                    webView.width,
                                    webView.height,
                                    Bitmap.Config.ARGB_8888
                                )
                                val canvas = Canvas(bitmap)
                                webView.draw(canvas)
                                bitmap
                            } catch (e: Exception) {
                                Log.w(TAG, "获取WebView截图失败", e)
                                null
                            }
                        }
                    )
                }

                // 更新两个预览器
                materialWaveTracker?.updateWebViewCards(waveTrackerCardDataList)
                stackedCardPreview?.setWebViewCards(stackedCardDataList)
                Log.d(TAG, "✅ 更新了 ${stackedCardDataList.size} 个卡片到预览器")
            } else {
                Log.d(TAG, "没有卡片需要更新到预览器")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 更新卡片预览器数据失败", e)
        }
    }

    /**
     * 切换到指定的webview卡片
     */
    private fun switchToWebViewCard(cardIndex: Int) {
        try {
            gestureCardWebViewManager?.let { manager ->
                val allCards = manager.getAllCards()
                if (cardIndex >= 0 && cardIndex < allCards.size) {
                    manager.switchToCard(cardIndex)
                    Log.d(TAG, "✅ 通过卡片预览切换到卡片: $cardIndex")

                    // 更新卡片数据（可能有变化）
                    updateWaveTrackerCards()
                } else {
                    Log.w(TAG, "⚠️ 无效的卡片索引: $cardIndex")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 切换到webview卡片失败", e)
        }
    }

    /**
     * 关闭指定的webview卡片
     */
    private fun closeWebViewCard(cardIndex: Int) {
        try {
            gestureCardWebViewManager?.let { manager ->
                val allCards = manager.getAllCards()
                if (cardIndex >= 0 && cardIndex < allCards.size) {
                    val cardData = allCards[cardIndex]

                    // 关闭webview
                    cardData.webView?.destroy()

                    // 从管理器中移除卡片
                    manager.removeCard(cardIndex)

                    Log.d(TAG, "✅ 关闭webview卡片: $cardIndex (${cardData.title})")

                    // 更新卡片数据
                    updateWaveTrackerCards()
                    
                    // 检查是否还有卡片
                    val remainingCards = manager.getAllCards()
                    if (remainingCards.isEmpty()) {
                        // 没有卡片了，隐藏预览并返回搜索tab
                        stackedCardPreview?.visibility = View.GONE
                        // 清理所有预览器的卡片数据
                        materialWaveTracker?.updateWebViewCards(emptyList())
                        stackedCardPreview?.setWebViewCards(emptyList())
                        browserLayout.postDelayed({
                            switchToSearchTab()
                            Log.d(TAG, "最后一个卡片已关闭，返回搜索tab")
                        }, 300)
                    } else {
                        // 还有卡片，继续显示预览
                        Log.d(TAG, "还有 ${remainingCards.size} 个卡片")
                    }
                } else {
                    Log.w(TAG, "⚠️ 无效的卡片索引: $cardIndex")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 关闭webview卡片失败", e)
        }
    }

    /**
     * 刷新指定的webview卡片
     */
    private fun refreshWebViewCard(cardIndex: Int) {
        try {
            gestureCardWebViewManager?.let { manager ->
                val allCards = manager.getAllCards()
                if (cardIndex >= 0 && cardIndex < allCards.size) {
                    val cardData = allCards[cardIndex]

                    // 刷新webview
                    cardData.webView?.reload()

                    Log.d(TAG, "✅ 刷新webview卡片: $cardIndex (${cardData.title})")

                    // 显示刷新提示
                    runOnUiThread {
                        Toast.makeText(this@SimpleModeActivity, "页面已刷新", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Log.w(TAG, "⚠️ 无效的卡片索引: $cardIndex")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 刷新webview卡片失败", e)
        }
    }





    /**
     * 为单个tab设置手势检测
     */
    private fun setupTabGestureDetection(tabView: LinearLayout, gestureDetector: GestureDetector) {
        tabView.setOnTouchListener { view, event ->
            // 如果搜索tab手势遮罩区激活，且这是搜索tab，则优先处理原有的多卡片激活机制
            val isSearchTab = view.id == R.id.tab_search
            val isCurrentlyInSearchTab = getCurrentTabIndex() == 1

            // 检查是否有悬浮卡片预览正在显示
            val isStackedPreviewVisible = stackedCardPreview?.visibility == View.VISIBLE

            if (isStackedPreviewVisible) {
                // 悬浮卡片预览模式下，将触摸事件传递给StackedCardPreview处理
                Log.d(TAG, "tab区域触摸，悬浮卡片预览可见，传递触摸事件给StackedCardPreview")

                // 将触摸坐标转换为StackedCardPreview的坐标系
                stackedCardPreview?.let { preview ->
                    val location = IntArray(2)
                    view.getLocationOnScreen(location)
                    val previewLocation = IntArray(2)
                    preview.getLocationOnScreen(previewLocation)

                    val relativeX = location[0] - previewLocation[0] + event.x
                    val relativeY = location[1] - previewLocation[1] + event.y

                    // 创建相对坐标的触摸事件
                    val relativeEvent = MotionEvent.obtain(
                        event.downTime,
                        event.eventTime,
                        event.action,
                        relativeX,
                        relativeY,
                        event.metaState
                    )

                    // 传递给StackedCardPreview处理长按滑动
                    val handled = preview.dispatchTouchEvent(relativeEvent)
                    relativeEvent.recycle()

                    Log.d(TAG, "tab触摸事件传递结果: $handled, 动作: ${event.action}")
                    return@setOnTouchListener handled
                } ?: false
            }

            // 只有当前已经在搜索tab且点击的是搜索tab时，才显示层叠卡片预览效果
            if (isSearchTab && isCurrentlyInSearchTab) {
                // 搜索tab - 显示层叠卡片预览效果
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        // 显示层叠卡片预览器，但确保不阻挡底部导航栏
                        stackedCardPreview?.apply {
                            visibility = View.VISIBLE
                            // 强制设置为不可交互
                            isClickable = false
                            isFocusable = false
                            isFocusableInTouchMode = false
                            isEnabled = false
                            // 确保触摸事件穿透
                            setOnTouchListener { _, _ -> false }
                        }

                        // 将触摸坐标转换为层叠卡片预览器的坐标系
                        val location = IntArray(2)
                        view.getLocationOnScreen(location)
                        val stackedLocation = IntArray(2)
                        stackedCardPreview?.getLocationOnScreen(stackedLocation)

                        val relativeX = location[0] - stackedLocation[0] + event.x
                        val relativeY = location[1] - stackedLocation[1] + event.y

                        // 更新手指位置，显示卡片预览
                        stackedCardPreview?.updateFingerPosition(relativeX, relativeY)

                        // 确保卡片数据是最新的
                        updateWaveTrackerCards()
                    }
                    MotionEvent.ACTION_MOVE -> {
                        // 继续更新手指位置
                        val location = IntArray(2)
                        view.getLocationOnScreen(location)
                        val stackedLocation = IntArray(2)
                        stackedCardPreview?.getLocationOnScreen(stackedLocation)

                        val relativeX = location[0] - stackedLocation[0] + event.x
                        val relativeY = location[1] - stackedLocation[1] + event.y

                        stackedCardPreview?.updateFingerPosition(relativeX, relativeY)
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        // 停止卡片预览效果并隐藏
                        stackedCardPreview?.stopWave()
                        stackedCardPreview?.visibility = View.GONE
                    }
                }
            }

            // 处理手势检测（横滑切换）
            gestureDetector.onTouchEvent(event)

            // 不消费事件，让点击事件继续传递
            false
        }
    }



    /**
     * 测试webview卡片切换功能
     */
    private fun testWebViewCardSwitching() {
        Log.d(TAG, "🧪 开始测试webview卡片切换功能")

        gestureCardWebViewManager?.let { manager ->
            val allCards = manager.getAllCards()
            val totalPages = allCards.size

            Log.d(TAG, "📊 测试结果 - 总页面数: $totalPages")
            Log.d(TAG, "📋 所有卡片: ${allCards.map { it.title ?: "无标题" }}")

            val message = "测试结果：共有 $totalPages 个页面"
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()

            if (totalPages > 1) {
                Log.d(TAG, "🔄 测试切换到下一页")
                switchToNextWebPage()
            } else if (totalPages == 1) {
                Log.d(TAG, "⚠️ 只有一个页面，无法测试切换")
                Toast.makeText(this, "只有一个页面，请先打开多个网页", Toast.LENGTH_LONG).show()
            } else {
                Log.d(TAG, "⚠️ 没有页面")
                Toast.makeText(this, "没有打开的页面，请先搜索一些内容", Toast.LENGTH_LONG).show()
            }
        } ?: run {
            Log.w(TAG, "❌ gestureCardWebViewManager为null")
            Toast.makeText(this, "页面管理器未初始化", Toast.LENGTH_LONG).show()
        }
    }



    /**
     * 切换到上一个tab
     */
    private fun switchToPreviousTab() {
        val currentTab = getCurrentTabIndex()
        val visibleTabs = getVisibleTabs()
        val currentIndex = visibleTabs.indexOf(currentTab)

        if (currentIndex > 0) {
            val previousTab = visibleTabs[currentIndex - 1]
            switchToTab(previousTab)
            Log.d(TAG, "横滑切换到上一个tab: $previousTab")
        }
    }

    /**
     * 切换到下一个tab
     */
    private fun switchToNextTab() {
        val currentTab = getCurrentTabIndex()
        val visibleTabs = getVisibleTabs()
        val currentIndex = visibleTabs.indexOf(currentTab)

        if (currentIndex < visibleTabs.size - 1) {
            val nextTab = visibleTabs[currentIndex + 1]
            switchToTab(nextTab)
            Log.d(TAG, "横滑切换到下一个tab: $nextTab")
        }
    }

    /**
     * 获取当前tab索引
     */
    private fun getCurrentTabIndex(): Int {
        return when (currentState) {
            UIState.CHAT -> 0
            UIState.BROWSER -> 1
            UIState.TASK_SELECTION -> 2
            UIState.VOICE -> 3
            UIState.APP_SEARCH -> 4
            UIState.SETTINGS -> 5
            else -> 0
        }
    }

    /**
     * 获取可见的tab列表
     */
    private fun getVisibleTabs(): List<Int> {
        val tabs = mutableListOf<Int>()
        tabs.add(0) // 对话
        tabs.add(1) // 搜索
        tabs.add(2) // 任务

        // 检查语音tab是否可见
        val voiceTab = findViewById<LinearLayout>(R.id.tab_voice)
        if (voiceTab?.visibility == View.VISIBLE) {
            tabs.add(3) // 语音
        }

        tabs.add(4) // 软件
        tabs.add(5) // 设置

        return tabs
    }

    /**
     * 切换到指定tab
     */
    private fun switchToTab(tabIndex: Int) {
        when (tabIndex) {
            0 -> showChat()
            1 -> showBrowser()
            2 -> showTaskSelection()
            3 -> showVoice()
            4 -> showAppSearch()
            5 -> showSettings()
        }
    }

    /**
     * 切换到搜索tab
     */
    private fun switchToSearchTab() {
        try {
            deactivateStackedCardPreview()
            showBrowser()
            // 确保显示搜索tab主页
            showBrowserHome()
            Log.d(TAG, "切换到搜索tab并显示主页")
        } catch (e: Exception) {
            Log.e(TAG, "切换到搜索tab失败", e)
        }
    }

    /**
     * 当前WebView后退
     */
    private fun goBackInCurrentWebView() {
        try {
            gestureCardWebViewManager?.getCurrentCard()?.webView?.let { webView ->
                if (webView.canGoBack()) {
                    webView.goBack()
                    Log.d(TAG, "WebView后退成功")
                } else {
                    Log.d(TAG, "WebView无法后退")
                    Toast.makeText(this, "无法后退", Toast.LENGTH_SHORT).show()
                }
            } ?: run {
                Log.d(TAG, "没有活动的WebView")
                Toast.makeText(this, "没有活动的网页", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "WebView后退失败", e)
        }
    }

    /**
     * 当前WebView前进
     */
    private fun goForwardInCurrentWebView() {
        try {
            gestureCardWebViewManager?.getCurrentCard()?.webView?.let { webView ->
                if (webView.canGoForward()) {
                    webView.goForward()
                    Log.d(TAG, "WebView前进成功")
                } else {
                    Log.d(TAG, "WebView无法前进")
                    Toast.makeText(this, "无法前进", Toast.LENGTH_SHORT).show()
                }
            } ?: run {
                Log.d(TAG, "没有活动的WebView")
                Toast.makeText(this, "没有活动的网页", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "WebView前进失败", e)
        }
    }

    // ==================== 输入法控制方法 ====================

    /**
     * 显示软键盘
     */
    private fun showKeyboard(editText: EditText) {
        try {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
            Log.d(TAG, "显示软键盘")
        } catch (e: Exception) {
            Log.e(TAG, "显示软键盘失败", e)
        }
    }

    /**
     * 隐藏软键盘
     */
    private fun hideKeyboard(view: View) {
        try {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken, 0)
            Log.d(TAG, "隐藏软键盘")
        } catch (e: Exception) {
            Log.e(TAG, "隐藏软键盘失败", e)
        }
    }

    // ==================== 底部tab手势指示器 ====================

    /**
     * 创建底部tab区域的手势指示器
     */
    private fun createTabSwipeGestureIndicator() {
        try {
            // 创建手势指示器容器
            val indicatorContainer = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER
                setPadding(16, 8, 16, 8)
            }

            // 创建左箭头
            val leftArrow = TextView(this).apply {
                text = "◀"
                textSize = 16f
                setTextColor(android.graphics.Color.WHITE)
                alpha = 0.7f
            }

            // 创建中间提示文字
            val hintText = TextView(this).apply {
                text = "左右滑动切换"
                textSize = 12f
                setTextColor(android.graphics.Color.WHITE)
                alpha = 0.8f
                setPadding(16, 0, 16, 0)
            }

            // 创建右箭头
            val rightArrow = TextView(this).apply {
                text = "▶"
                textSize = 16f
                setTextColor(android.graphics.Color.WHITE)
                alpha = 0.7f
            }

            // 添加到容器
            indicatorContainer.addView(leftArrow)
            indicatorContainer.addView(hintText)
            indicatorContainer.addView(rightArrow)

            // 创建指示器背景
            tabSwipeGestureIndicator = FrameLayout(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
                    bottomMargin = 120 // 在底部tab上方显示
                }

                // 设置背景
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(android.graphics.Color.parseColor("#80000000"))
                    cornerRadius = 20f
                }

                addView(indicatorContainer)
                visibility = View.GONE
            }

            // 添加到主布局
            val rootLayout = findViewById<FrameLayout>(android.R.id.content)
            rootLayout.addView(tabSwipeGestureIndicator)

            Log.d(TAG, "底部tab手势指示器创建完成")
        } catch (e: Exception) {
            Log.e(TAG, "创建底部tab手势指示器失败", e)
        }
    }

    /**
     * 显示底部tab手势指示器
     */
    private fun showTabSwipeGestureIndicator() {
        tabSwipeGestureIndicator?.apply {
            visibility = View.VISIBLE
            alpha = 0f
            scaleX = 0.8f
            scaleY = 0.8f

            animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(200)
                .start()

            // 2秒后自动隐藏
            postDelayed({
                hideTabSwipeGestureIndicator()
            }, 2000)
        }
    }

    /**
     * 隐藏底部tab手势指示器
     */
    private fun hideTabSwipeGestureIndicator() {
        tabSwipeGestureIndicator?.animate()
            ?.alpha(0f)
            ?.scaleX(0.8f)
            ?.scaleY(0.8f)
            ?.setDuration(200)
            ?.withEndAction {
                tabSwipeGestureIndicator?.visibility = View.GONE
            }
            ?.start()
    }

    /**
     * 确保AI助手标签页存在
     */
    private fun ensureAIAssistantTabExists() {
        try {
            val chatTabLayout = findViewById<com.google.android.material.tabs.TabLayout>(R.id.chat_tab_layout)

            // 检查是否已存在"AI助手"标签页
            var aiTabExists = false
            for (i in 0 until (chatTabLayout?.tabCount ?: 0)) {
                val tab = chatTabLayout?.getTabAt(i)
                if (tab?.text?.toString() == "AI助手") {
                    aiTabExists = true
                    break
                }
            }

            // 如果不存在，创建"AI助手"标签页
            if (!aiTabExists) {
                val aiTab = chatTabLayout?.newTab()?.setText("AI助手")
                if (aiTab != null) {
                    // 在"全部"标签页后面插入
                    chatTabLayout?.addTab(aiTab, 1)
                    Log.d(TAG, "创建AI助手标签页")

                    // 清除删除标记
                    val prefs = getSharedPreferences("custom_tabs", MODE_PRIVATE)
                    prefs.edit().putBoolean("ai_assistant_group_deleted", false).apply()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "确保AI助手标签页存在失败", e)
        }
    }

    // ==================== 搜索tab横滑热区 ====================

    /**
     * 创建搜索tab横滑热区
     */
    private fun createSearchTabSwipeHotArea() {
        try {
            // 使用浏览器WebView容器作为搜索tab容器
            val searchTabContainer = findViewById<FrameLayout>(R.id.browser_webview_container)
            if (searchTabContainer == null) {
                Log.w(TAG, "搜索tab容器未找到，无法创建横滑热区")
                return
            }

            // 创建横滑热区（透明覆盖层）
            searchTabSwipeHotArea = View(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    120 // 热区高度120dp，避免覆盖搜索tab区域
                ).apply {
                    gravity = android.view.Gravity.TOP
                    topMargin = 200 // 距离顶部200dp开始，确保不覆盖搜索tab
                }

                // 设置透明背景
                setBackgroundColor(android.graphics.Color.TRANSPARENT)

                // 设置触摸监听
                setOnTouchListener(createSearchTabSwipeGestureListener())
            }

            // 添加到搜索tab容器
            searchTabContainer.addView(searchTabSwipeHotArea)

            // 创建横滑指示器
            createSearchTabSwipeIndicator()

            Log.d(TAG, "搜索tab横滑热区创建完成")
        } catch (e: Exception) {
            Log.e(TAG, "创建搜索tab横滑热区失败", e)
        }
    }

    /**
     * 创建搜索tab横滑指示器
     */
    private fun createSearchTabSwipeIndicator() {
        try {
            // 创建指示器容器
            val indicatorContainer = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER
                setPadding(20, 10, 20, 10)
            }

            // 创建左箭头
            val leftArrow = TextView(this).apply {
                text = "◀"
                textSize = 18f
                setTextColor(android.graphics.Color.WHITE)
                alpha = 0.8f
            }

            // 创建中间提示文字
            val hintText = TextView(this).apply {
                text = "左右滑动切换页面"
                textSize = 14f
                setTextColor(android.graphics.Color.WHITE)
                alpha = 0.9f
                setPadding(20, 0, 20, 0)
            }

            // 创建右箭头
            val rightArrow = TextView(this).apply {
                text = "▶"
                textSize = 18f
                setTextColor(android.graphics.Color.WHITE)
                alpha = 0.8f
            }

            // 添加到容器
            indicatorContainer.addView(leftArrow)
            indicatorContainer.addView(hintText)
            indicatorContainer.addView(rightArrow)

            // 创建指示器背景
            searchTabSwipeIndicator = FrameLayout(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = android.view.Gravity.CENTER_HORIZONTAL or android.view.Gravity.TOP
                    topMargin = 150 // 在热区中央显示
                }

                // 设置背景
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(android.graphics.Color.parseColor("#CC000000"))
                    cornerRadius = 25f
                }

                addView(indicatorContainer)
                visibility = View.GONE
            }

            // 添加到主布局
            val rootLayout = findViewById<FrameLayout>(android.R.id.content)
            rootLayout.addView(searchTabSwipeIndicator)

            Log.d(TAG, "搜索tab横滑指示器创建完成")
        } catch (e: Exception) {
            Log.e(TAG, "创建搜索tab横滑指示器失败", e)
        }
    }

    /**
     * 创建搜索tab横滑手势监听器
     */
    private fun createSearchTabSwipeGestureListener(): View.OnTouchListener {
        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false

                // 检查是否有悬浮卡片预览正在显示
                val isStackedPreviewVisible = stackedCardPreview?.visibility == View.VISIBLE
                if (isStackedPreviewVisible) {
                    // 悬浮卡片显示时，不处理页面切换手势
                    Log.d(TAG, "悬浮卡片预览可见，不处理页面切换手势")
                    return false
                }

                // 防抖处理
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastSearchTabSwipeTime < swipeDebounceDelay) {
                    Log.d(TAG, "搜索tab横滑手势防抖，忽略此次操作")
                    return false
                }

                val deltaX = e2.x - e1.x
                val deltaY = e2.y - e1.y

                // 确保是水平滑动且滑动距离足够
                if (abs(deltaX) > abs(deltaY) && abs(deltaX) > 120) {
                    lastSearchTabSwipeTime = currentTime

                    if (deltaX > 0) {
                        // 向右滑动，切换到上一个页面
                        switchToPreviousWebPage()
                        showSearchTabSwipeIndicator("上一页")
                    } else {
                        // 向左滑动，切换到下一个页面
                        switchToNextWebPage()
                        showSearchTabSwipeIndicator("下一页")
                    }
                    return true
                }
                return false
            }

            override fun onDown(e: MotionEvent): Boolean {
                // 检查是否有悬浮卡片预览正在显示
                val isStackedPreviewVisible = stackedCardPreview?.visibility == View.VISIBLE
                if (!isStackedPreviewVisible) {
                    // 只有在没有悬浮卡片时才显示手势提示
                    showSearchTabSwipeIndicator("滑动切换")
                }
                return true
            }
        })

        return View.OnTouchListener { view, event ->
            // 检查是否有悬浮卡片预览正在显示
            val isStackedPreviewVisible = stackedCardPreview?.visibility == View.VISIBLE

            if (isStackedPreviewVisible) {
                // 悬浮卡片预览模式下，将触摸事件传递给StackedCardPreview处理
                Log.d(TAG, "搜索tab区域触摸，悬浮卡片预览可见，传递触摸事件给StackedCardPreview")

                // 将触摸坐标转换为StackedCardPreview的坐标系
                stackedCardPreview?.let { preview ->
                    val location = IntArray(2)
                    view.getLocationOnScreen(location)
                    val previewLocation = IntArray(2)
                    preview.getLocationOnScreen(previewLocation)

                    val relativeX = location[0] - previewLocation[0] + event.x
                    val relativeY = location[1] - previewLocation[1] + event.y

                    // 创建相对坐标的触摸事件
                    val relativeEvent = MotionEvent.obtain(
                        event.downTime,
                        event.eventTime,
                        event.action,
                        relativeX,
                        relativeY,
                        event.metaState
                    )

                    // 传递给StackedCardPreview处理长按滑动
                    val handled = preview.dispatchTouchEvent(relativeEvent)
                    relativeEvent.recycle()

                    Log.d(TAG, "搜索tab触摸事件传递结果: $handled, 动作: ${event.action}")
                    return@OnTouchListener handled
                } ?: false
            } else {
                // 正常模式下，处理页面切换手势
                gestureDetector.onTouchEvent(event)
            }
        }
    }

    /**
     * 显示搜索tab横滑指示器
     */
    private fun showSearchTabSwipeIndicator(action: String = "滑动切换") {
        searchTabSwipeIndicator?.apply {
            // 更新提示文字和箭头 - 查找LinearLayout中的TextView
            val frameLayout = this as FrameLayout
            val container = frameLayout.getChildAt(0) as? LinearLayout
            val leftArrow = container?.getChildAt(0) as? TextView
            val hintText = container?.getChildAt(1) as? TextView // 中间的提示文字
            val rightArrow = container?.getChildAt(2) as? TextView

            // 更新文字和箭头显示
            when (action) {
                "上一页" -> {
                    hintText?.text = "上一页"
                    leftArrow?.visibility = View.VISIBLE
                    rightArrow?.visibility = View.GONE
                    leftArrow?.text = "◀"
                }
                "下一页" -> {
                    hintText?.text = "下一页"
                    leftArrow?.visibility = View.GONE
                    rightArrow?.visibility = View.VISIBLE
                    rightArrow?.text = "▶"
                }
                else -> {
                    hintText?.text = "左右滑动切换页面"
                    leftArrow?.visibility = View.VISIBLE
                    rightArrow?.visibility = View.VISIBLE
                    leftArrow?.text = "◀"
                    rightArrow?.text = "▶"
                }
            }

            visibility = View.VISIBLE
            alpha = 0f
            scaleX = 0.8f
            scaleY = 0.8f
            translationY = 20f

            // Material风格的动画
            animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .translationY(0f)
                .setDuration(300)
                .setInterpolator(android.view.animation.DecelerateInterpolator(2f))
                .withEndAction {
                    // 添加箭头动画
                    animateArrows(leftArrow, rightArrow, action)
                }
                .start()

            // 2秒后自动隐藏
            postDelayed({
                hideSearchTabSwipeIndicator()
            }, 2000)
        }
    }

    /**
     * 隐藏搜索tab横滑指示器
     */
    private fun hideSearchTabSwipeIndicator() {
        searchTabSwipeIndicator?.animate()
            ?.alpha(0f)
            ?.scaleX(0.8f)
            ?.scaleY(0.8f)
            ?.translationY(20f)
            ?.setDuration(200)
            ?.setInterpolator(android.view.animation.AccelerateInterpolator(2f))
            ?.withEndAction {
                searchTabSwipeIndicator?.visibility = View.GONE
            }
            ?.start()
    }

    /**
     * 动画箭头
     */
    private fun animateArrows(leftArrow: TextView?, rightArrow: TextView?, action: String) {
        when (action) {
            "上一页" -> {
                leftArrow?.let { arrow ->
                    // 左箭头向右移动的动画
                    arrow.animate()
                        .translationX(10f)
                        .setDuration(200)
                        .withEndAction {
                            arrow.animate()
                                .translationX(0f)
                                .setDuration(200)
                                .start()
                        }
                        .start()
                }
            }
            "下一页" -> {
                rightArrow?.let { arrow ->
                    // 右箭头向左移动的动画
                    arrow.animate()
                        .translationX(-10f)
                        .setDuration(200)
                        .withEndAction {
                            arrow.animate()
                                .translationX(0f)
                                .setDuration(200)
                                .start()
                        }
                        .start()
                }
            }
            else -> {
                // 左右箭头交替动画
                leftArrow?.let { left ->
                    left.animate()
                        .translationX(5f)
                        .setDuration(300)
                        .withEndAction {
                            left.animate()
                                .translationX(0f)
                                .setDuration(300)
                                .start()
                        }
                        .start()
                }
                rightArrow?.let { right ->
                    right.animate()
                        .translationX(-5f)
                        .setDuration(300)
                        .withEndAction {
                            right.animate()
                                .translationX(0f)
                                .setDuration(300)
                                .start()
                        }
                        .start()
                }
            }
        }
    }

    /**
     * 设置tab区域之间的滑动切换网页功能
     */
    private fun setupTabAreaSwipeForWebPageSwitch() {
        try {
            // 创建tab区域滑动手势检测器
            val tabSwipeDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
                override fun onFling(
                    e1: MotionEvent?,
                    e2: MotionEvent,
                    velocityX: Float,
                    velocityY: Float
                ): Boolean {
                    if (e1 == null) return false

                    // 防抖处理
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastTabSwipeTime < tabSwipeDebounceDelay) {
                        return false
                    }

                    val deltaX = e2.x - e1.x
                    val deltaY = e2.y - e1.y

                    // 确保是水平滑动且滑动距离足够
                    if (abs(deltaX) > abs(deltaY) && abs(deltaX) > 120) {
                        lastTabSwipeTime = currentTime

                        if (deltaX > 0) {
                            // 向右滑动，切换到上一个网页
                            switchToPreviousWebPage()
                            showTabSwipeIndicator("上一个标签")
                        } else {
                            // 向左滑动，切换到下一个网页
                            switchToNextWebPage()
                            showTabSwipeIndicator("下一个标签")
                        }
                        return true
                    }
                    return false
                }

                override fun onDown(e: MotionEvent): Boolean {
                    // 显示手势提示
                    showTabSwipeIndicator("左右滑动切换网页")
                    return true
                }
            })

            // 为底部导航栏设置手势监听
            val bottomNav = findViewById<LinearLayout>(R.id.bottom_navigation)
            bottomNav?.setOnTouchListener { _, event ->
                // 只有在搜索tab时才处理网页切换手势
                if (getCurrentTabIndex() == 1) {
                    tabSwipeDetector.onTouchEvent(event)
                } else {
                    false
                }
            }

            Log.d(TAG, "Tab区域滑动切换网页功能设置完成")
        } catch (e: Exception) {
            Log.e(TAG, "设置tab区域滑动切换网页功能失败", e)
        }
    }

    /**
     * 创建tab滑动指示器
     */
    private fun createTabSwipeIndicator() {
        try {
            // 创建指示器容器
            val indicatorContainer = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(24, 16, 24, 16)
            }

            // 左箭头
            val leftArrow = TextView(this).apply {
                text = "◀"
                textSize = 20f
                setTextColor(ContextCompat.getColor(this@SimpleModeActivity, R.color.material_green_500))
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = 8
                }
            }

            // 提示文字
            val hintText = TextView(this).apply {
                text = "左右滑动切换网页"
                textSize = 16f
                setTextColor(ContextCompat.getColor(this@SimpleModeActivity, R.color.material_grey_800))
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = 8
                    marginEnd = 8
                }
            }

            // 右箭头
            val rightArrow = TextView(this).apply {
                text = "▶"
                textSize = 20f
                setTextColor(ContextCompat.getColor(this@SimpleModeActivity, R.color.material_green_500))
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = 8
                }
            }

            indicatorContainer.addView(leftArrow)
            indicatorContainer.addView(hintText)
            indicatorContainer.addView(rightArrow)

            // 创建指示器背景
            tabSwipeIndicator = FrameLayout(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = android.view.Gravity.CENTER_HORIZONTAL or android.view.Gravity.TOP
                    topMargin = 150 // 在tab区域上方显示
                }

                // 设置背景
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(ContextCompat.getColor(this@SimpleModeActivity, R.color.material_grey_100))
                    cornerRadius = 24f
                    setStroke(2, ContextCompat.getColor(this@SimpleModeActivity, R.color.material_green_500))
                }

                addView(indicatorContainer)
                visibility = View.GONE
            }

            // 添加到主布局
            val rootLayout = findViewById<FrameLayout>(android.R.id.content)
            rootLayout.addView(tabSwipeIndicator)

            Log.d(TAG, "Tab滑动指示器创建完成")
        } catch (e: Exception) {
            Log.e(TAG, "创建tab滑动指示器失败", e)
        }
    }

    /**
     * 显示tab滑动指示器
     */
    private fun showTabSwipeIndicator(action: String = "左右滑动切换网页") {
        tabSwipeIndicator?.apply {
            // 更新提示文字和箭头
            val frameLayout = this as FrameLayout
            val container = frameLayout.getChildAt(0) as? LinearLayout
            val leftArrow = container?.getChildAt(0) as? TextView
            val hintText = container?.getChildAt(1) as? TextView
            val rightArrow = container?.getChildAt(2) as? TextView

            // 更新文字和箭头显示
            when (action) {
                "上一个标签" -> {
                    hintText?.text = "上一个标签"
                    leftArrow?.visibility = View.VISIBLE
                    rightArrow?.visibility = View.GONE
                    leftArrow?.text = "◀"
                }
                "下一个标签" -> {
                    hintText?.text = "下一个标签"
                    leftArrow?.visibility = View.GONE
                    rightArrow?.visibility = View.VISIBLE
                    rightArrow?.text = "▶"
                }
                else -> {
                    hintText?.text = "左右滑动切换网页"
                    leftArrow?.visibility = View.VISIBLE
                    rightArrow?.visibility = View.VISIBLE
                    leftArrow?.text = "◀"
                    rightArrow?.text = "▶"
                }
            }

            visibility = View.VISIBLE
            alpha = 0f
            scaleX = 0.8f
            scaleY = 0.8f
            translationY = 20f

            // Material风格的动画
            animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .translationY(0f)
                .setDuration(300)
                .setInterpolator(android.view.animation.DecelerateInterpolator(2f))
                .withEndAction {
                    // 添加箭头动画
                    animateTabArrows(leftArrow, rightArrow, action)
                }
                .start()

            // 2秒后自动隐藏
            postDelayed({
                hideTabSwipeIndicator()
            }, 2000)
        }
    }

    /**
     * 隐藏tab滑动指示器
     */
    private fun hideTabSwipeIndicator() {
        tabSwipeIndicator?.animate()
            ?.alpha(0f)
            ?.scaleX(0.8f)
            ?.scaleY(0.8f)
            ?.translationY(20f)
            ?.setDuration(200)
            ?.setInterpolator(android.view.animation.AccelerateInterpolator(2f))
            ?.withEndAction {
                tabSwipeIndicator?.visibility = View.GONE
            }
            ?.start()
    }

    /**
     * 动画tab箭头
     */
    private fun animateTabArrows(leftArrow: TextView?, rightArrow: TextView?, action: String) {
        when (action) {
            "上一个标签" -> {
                leftArrow?.let { arrow ->
                    // 左箭头向右移动的动画
                    arrow.animate()
                        .translationX(10f)
                        .setDuration(200)
                        .withEndAction {
                            arrow.animate()
                                .translationX(0f)
                                .setDuration(200)
                                .start()
                        }
                        .start()
                }
            }
            "下一个标签" -> {
                rightArrow?.let { arrow ->
                    // 右箭头向左移动的动画
                    arrow.animate()
                        .translationX(-10f)
                        .setDuration(200)
                        .withEndAction {
                            arrow.animate()
                                .translationX(0f)
                                .setDuration(200)
                                .start()
                        }
                        .start()
                }
            }
            else -> {
                // 左右箭头交替动画
                leftArrow?.let { left ->
                    left.animate()
                        .translationX(5f)
                        .setDuration(300)
                        .withEndAction {
                            left.animate()
                                .translationX(0f)
                                .setDuration(300)
                                .start()
                        }
                        .start()
                }
                rightArrow?.let { right ->
                    right.animate()
                        .translationX(-5f)
                        .setDuration(300)
                        .withEndAction {
                            right.animate()
                                .translationX(0f)
                                .setDuration(300)
                                .start()
                        }
                        .start()
                }
            }
        }
    }

    /**
     * 切换到上一个网页
     */
    private fun switchToPreviousWebPage() {
        Log.d(TAG, "🔄 开始切换到上一个网页")
        try {
            gestureCardWebViewManager?.let { manager ->
                val allCards = manager.getAllCards()
                val currentCard = manager.getCurrentCard()
                val currentIndex = allCards.indexOf(currentCard)
                val totalPages = allCards.size

                Log.d(TAG, "📊 切换到上一个网页 - 当前索引: $currentIndex, 总页面数: $totalPages")
                Log.d(TAG, "📋 所有卡片: ${allCards.map { it.title ?: "无标题" }}")

                if (totalPages > 1) {
                    val previousIndex = if (currentIndex > 0) currentIndex - 1 else totalPages - 1
                    manager.switchToCard(previousIndex)

                    Log.d(TAG, "✅ 成功切换到上一个网页: $previousIndex")

                    // 显示页面切换动画
                    showPageSwitchAnimation("上一页", previousIndex + 1, totalPages)

                    // 静默切换，不显示提示
                } else if (totalPages == 1) {
                    Log.d(TAG, "⚠️ 只有一个页面，无法切换")
                } else {
                    Log.d(TAG, "⚠️ 没有页面")
                }
            } ?: run {
                Log.w(TAG, "❌ gestureCardWebViewManager为null，无法切换页面")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 切换到上一个网页失败", e)
        }
    }

    /**
     * 切换到下一个网页
     */
    private fun switchToNextWebPage() {
        Log.d(TAG, "🔄 开始切换到下一个网页")
        try {
            gestureCardWebViewManager?.let { manager ->
                val allCards = manager.getAllCards()
                val currentCard = manager.getCurrentCard()
                val currentIndex = allCards.indexOf(currentCard)
                val totalPages = allCards.size

                Log.d(TAG, "📊 切换到下一个网页 - 当前索引: $currentIndex, 总页面数: $totalPages")
                Log.d(TAG, "📋 所有卡片: ${allCards.map { it.title ?: "无标题" }}")

                if (totalPages > 1) {
                    val nextIndex = if (currentIndex < totalPages - 1) currentIndex + 1 else 0
                    manager.switchToCard(nextIndex)

                    Log.d(TAG, "✅ 成功切换到下一个网页: $nextIndex")

                    // 显示页面切换动画
                    showPageSwitchAnimation("下一页", nextIndex + 1, totalPages)

                    // 静默切换，不显示提示
                } else if (totalPages == 1) {
                    Log.d(TAG, "⚠️ 只有一个页面，无法切换")
                } else {
                    Log.d(TAG, "⚠️ 没有页面")
                }
            } ?: run {
                Log.w(TAG, "❌ gestureCardWebViewManager为null，无法切换页面")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 切换到下一个网页失败", e)
        }
    }

    // ==================== 对话tab横滑手势处理 ====================

    /**
     * 切换到上一个对话标签
     * @return 目标标签的名称
     */
    private fun switchToPreviousChatTab(): String? {
        try {
            val chatTabLayout = findViewById<com.google.android.material.tabs.TabLayout>(R.id.chat_tab_layout)
            if (chatTabLayout == null) {
                Log.w(TAG, "对话TabLayout未找到")
                return null
            }

            val currentPosition = chatTabLayout.selectedTabPosition
            val tabCount = chatTabLayout.tabCount

            if (tabCount <= 1) {
                Log.d(TAG, "只有一个标签，无法切换")
                return null
            }

            // 计算上一个标签位置（跳过"+"按钮）
            var previousPosition = currentPosition - 1
            if (previousPosition < 0) {
                // 如果已经是第一个标签，切换到最后一个有效标签（排除"+"按钮）
                previousPosition = tabCount - 2 // 倒数第二个（因为最后一个是"+"按钮）
                if (previousPosition < 0) previousPosition = 0
            }

            // 检查目标标签是否是"+"按钮，如果是则再往前一个
            val targetTab = chatTabLayout.getTabAt(previousPosition)
            if (targetTab?.text?.toString() == "+") {
                previousPosition = if (previousPosition > 0) previousPosition - 1 else tabCount - 2
            }

            // 切换到目标标签
            if (previousPosition >= 0 && previousPosition < tabCount) {
                val finalTargetTab = chatTabLayout.getTabAt(previousPosition)
                finalTargetTab?.select()
                val tabName = finalTargetTab?.text?.toString()
                Log.d(TAG, "✅ 切换到上一个对话标签: $previousPosition ($tabName)")
                return tabName
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 切换到上一个对话标签失败", e)
        }
        return null
    }

    /**
     * 切换到下一个对话标签
     * @return 目标标签的名称
     */
    private fun switchToNextChatTab(): String? {
        try {
            val chatTabLayout = findViewById<com.google.android.material.tabs.TabLayout>(R.id.chat_tab_layout)
            if (chatTabLayout == null) {
                Log.w(TAG, "对话TabLayout未找到")
                return null
            }

            val currentPosition = chatTabLayout.selectedTabPosition
            val tabCount = chatTabLayout.tabCount

            if (tabCount <= 1) {
                Log.d(TAG, "只有一个标签，无法切换")
                return null
            }

            // 计算下一个标签位置（跳过"+"按钮）
            var nextPosition = currentPosition + 1

            // 检查是否到达"+"按钮或超出范围
            if (nextPosition >= tabCount - 1) { // 最后一个是"+"按钮
                nextPosition = 0 // 回到第一个标签
            }

            // 再次检查目标标签是否是"+"按钮
            val targetTab = chatTabLayout.getTabAt(nextPosition)
            if (targetTab?.text?.toString() == "+") {
                nextPosition = 0 // 如果遇到"+"按钮，回到第一个标签
            }

            // 切换到目标标签
            if (nextPosition >= 0 && nextPosition < tabCount) {
                val finalTargetTab = chatTabLayout.getTabAt(nextPosition)
                finalTargetTab?.select()
                val tabName = finalTargetTab?.text?.toString()
                Log.d(TAG, "✅ 切换到下一个对话标签: $nextPosition ($tabName)")
                return tabName
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 切换到下一个对话标签失败", e)
        }
        return null
    }

    /**
     * 创建对话tab横滑指示器
     */
    private fun createChatTabSwipeIndicator() {
        try {
            // 创建指示器容器
            val indicatorContainer = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER
                setPadding(16, 8, 16, 8)
            }

            // 创建左箭头
            val leftArrow = TextView(this).apply {
                text = "◀"
                textSize = 16f
                setTextColor(android.graphics.Color.WHITE)
                alpha = 0.8f
            }

            // 创建中间提示文字
            val hintText = TextView(this).apply {
                text = "左右滑动切换标签"
                textSize = 14f
                setTextColor(android.graphics.Color.WHITE)
                alpha = 0.9f
                setPadding(20, 0, 20, 0)
            }

            // 创建右箭头
            val rightArrow = TextView(this).apply {
                text = "▶"
                textSize = 16f
                setTextColor(android.graphics.Color.WHITE)
                alpha = 0.8f
            }

            // 添加到容器
            indicatorContainer.addView(leftArrow)
            indicatorContainer.addView(hintText)
            indicatorContainer.addView(rightArrow)

            // 创建指示器背景
            chatTabSwipeIndicator = FrameLayout(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = android.view.Gravity.CENTER_HORIZONTAL or android.view.Gravity.TOP
                    topMargin = 200 // 距离顶部200dp，显示在对话tab区域附近
                }

                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(android.graphics.Color.parseColor("#CC000000"))
                    cornerRadius = 24f
                }

                addView(indicatorContainer)
                visibility = View.GONE
            }

            // 添加到主布局
            val rootLayout = findViewById<FrameLayout>(android.R.id.content)
            rootLayout.addView(chatTabSwipeIndicator)

            Log.d(TAG, "对话tab横滑指示器创建完成")
        } catch (e: Exception) {
            Log.e(TAG, "创建对话tab横滑指示器失败", e)
        }
    }

    /**
     * 显示对话tab横滑指示器
     */
    private fun showChatTabSwipeIndicator(tabName: String = "左右滑动切换标签") {
        chatTabSwipeIndicator?.apply {
            // 更新提示文字 - 查找LinearLayout中的TextView
            val frameLayout = this as FrameLayout
            val container = frameLayout.getChildAt(0) as? LinearLayout
            val hintText = container?.getChildAt(1) as? TextView // 中间的提示文字

            // 显示当前切换到的标签名称
            hintText?.text = if (tabName == "左右滑动切换标签") {
                tabName
            } else {
                "当前: $tabName"
            }

            visibility = View.VISIBLE
            alpha = 0f
            scaleX = 0.8f
            scaleY = 0.8f

            animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(200)
                .start()

            // 2秒后自动隐藏
            postDelayed({
                hideChatTabSwipeIndicator()
            }, 2000)
        }
    }

    /**
     * 隐藏对话tab横滑指示器
     */
    private fun hideChatTabSwipeIndicator() {
        chatTabSwipeIndicator?.animate()
            ?.alpha(0f)
            ?.scaleX(0.8f)
            ?.scaleY(0.8f)
            ?.setDuration(200)
            ?.withEndAction {
                chatTabSwipeIndicator?.visibility = View.GONE
            }
            ?.start()
    }

    /**
     * 显示页面切换动画
     */
    private fun showPageSwitchAnimation(direction: String, currentPage: Int, totalPages: Int) {
        try {
            // 创建页面切换提示
            val switchHint = TextView(this).apply {
                text = "$direction ($currentPage/$totalPages)"
                textSize = 16f
                setTextColor(android.graphics.Color.WHITE)
                setPadding(30, 15, 30, 15)
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(android.graphics.Color.parseColor("#DD000000"))
                    cornerRadius = 20f
                }
            }

            val switchIndicator = FrameLayout(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = android.view.Gravity.CENTER
                }
                addView(switchHint)
            }

            // 添加到主布局
            val rootLayout = findViewById<FrameLayout>(android.R.id.content)
            rootLayout.addView(switchIndicator)

            // 显示动画
            switchIndicator.alpha = 0f
            switchIndicator.scaleX = 0.8f
            switchIndicator.scaleY = 0.8f

            switchIndicator.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(200)
                .withEndAction {
                    // 1.5秒后隐藏
                    switchIndicator.postDelayed({
                        switchIndicator.animate()
                            .alpha(0f)
                            .scaleX(0.8f)
                            .scaleY(0.8f)
                            .setDuration(200)
                            .withEndAction {
                                rootLayout.removeView(switchIndicator)
                            }
                            .start()
                    }, 1500)
                }
                .start()

        } catch (e: Exception) {
            Log.e(TAG, "显示页面切换动画失败", e)
        }
    }

    // ==================== 卡片预览功能 ====================

    /**
     * 设置卡片预览按钮图标
     */
    private fun setupCardPreviewButtonIcon() {
        try {
            // 使用系统内置的网格视图图标作为卡片预览图标
            browserBtnClose.setImageResource(android.R.drawable.ic_dialog_dialer)

            // 设置按钮提示文字
            browserBtnClose.contentDescription = "卡片预览"

            Log.d(TAG, "卡片预览按钮图标设置完成")
        } catch (e: Exception) {
            Log.e(TAG, "设置卡片预览按钮图标失败", e)
            // 如果系统图标也不可用，使用默认图标
            try {
                browserBtnClose.setImageResource(android.R.drawable.ic_menu_view)
            } catch (e2: Exception) {
                Log.e(TAG, "设置备用图标也失败", e2)
            }
        }
    }

    /**
     * 显示卡片预览对话框
     */
    private fun showCardPreviewDialog() {
        Log.d(TAG, "showCardPreviewDialog 被调用")
        try {
            gestureCardWebViewManager?.let { manager ->
                val allCards = manager.getAllCards()
                val totalPages = allCards.size

                Log.d(TAG, "当前卡片数量: $totalPages")

                if (totalPages == 0) {
                    Toast.makeText(this, "没有打开的页面", Toast.LENGTH_SHORT).show()
                    return
                }

                // 激活搜索tab首页的卡片预览窗口
                activateSearchTabCardPreview()

                Log.d(TAG, "激活搜索tab卡片预览窗口，共 $totalPages 个页面")
            } ?: run {
                Log.w(TAG, "gestureCardWebViewManager 为 null")
                Toast.makeText(this, "卡片管理器未初始化", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "激活搜索tab卡片预览窗口失败", e)
            // 降级到简单预览
            showSimpleCardPreviewDialog()
        }
    }

    /**
     * 激活搜索tab首页的卡片预览窗口
     */
    private fun activateSearchTabCardPreview() {
        try {
            Log.d(TAG, "激活搜索tab卡片预览窗口")
            // 直接调用现有的showCardPreview方法
            showCardPreview()
        } catch (e: Exception) {
            Log.e(TAG, "激活搜索tab卡片预览窗口失败", e)
            // 如果预览模式不可用，显示简单对话框
            showSimpleCardPreviewDialog()
        }
    }

    /**
     * 激活层叠卡片预览（长按搜索tab触发）
     */
    private fun activateStackedCardPreview() {
        try {
            Log.d(TAG, "长按搜索tab，激活层叠卡片预览")

            // 合并所有管理器的卡片
            val gestureCards = gestureCardWebViewManager?.getAllCards() ?: emptyList()
            val mobileCards = mobileCardManager?.getAllCards() ?: emptyList()
            val allCards = mutableListOf<GestureCardWebViewManager.WebViewCardData>()

            allCards.addAll(gestureCards)
            allCards.addAll(mobileCards)

            Log.d(TAG, "激活层叠卡片预览 - 手势卡片: ${gestureCards.size}, 手机卡片: ${mobileCards.size}, 总计: ${allCards.size}")

            if (allCards.isEmpty()) {
                Toast.makeText(this, "没有打开的网页卡片", Toast.LENGTH_SHORT).show()
                return
            }

            // 显示层叠卡片预览
            stackedCardPreview?.apply {
                // 确保重置为层叠模式（不是悬浮模式）
                resetToStackedMode()

                // 更新卡片数据
                updateWaveTrackerCards()

                // 启用层叠预览模式的交互
                enableStackedInteraction()

                // 显示预览器
                visibility = View.VISIBLE

                Log.d(TAG, "层叠卡片预览已激活，显示 ${allCards.size} 张卡片，交互已启用")
            }

            // 给用户详细的操作提示
            val message = """
                已显示 ${allCards.size} 张网页卡片

                操作说明：
                • 长按并左右滑动：切换卡片
                • 长按并向上滑动：关闭卡片
                • 长按后松开：打开当前卡片
                • 快速滑动：惯性滚动
            """.trimIndent()

            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e(TAG, "激活层叠卡片预览失败", e)
            Toast.makeText(this, "激活卡片预览失败", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 处理层叠卡片预览的触摸事件
     */
    private fun handleStackedCardPreviewTouch(event: MotionEvent) {
        try {
            stackedCardPreview?.let { preview ->
                // 将触摸坐标转换为层叠卡片预览器的坐标系
                val location = IntArray(2)
                browserWebViewContainer.getLocationOnScreen(location)
                val previewLocation = IntArray(2)
                preview.getLocationOnScreen(previewLocation)

                val relativeX = location[0] - previewLocation[0] + event.x
                val relativeY = location[1] - previewLocation[1] + event.y

                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        Log.d(TAG, "层叠预览 - 手指按下: ($relativeX, $relativeY)")
                        // 更新手指位置，开始悬停检测
                        preview.updateFingerPosition(relativeX, relativeY)
                    }

                    MotionEvent.ACTION_MOVE -> {
                        Log.d(TAG, "层叠预览 - 手指移动: ($relativeX, $relativeY)")
                        // 继续更新手指位置，实时悬停检测
                        preview.updateFingerPosition(relativeX, relativeY)
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        Log.d(TAG, "层叠预览 - 手指抬起，进入悬浮模式")
                        // 手指抬起，停止预览并进入悬浮模式
                        preview.stopWave()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "处理层叠卡片预览触摸事件失败", e)
        }
    }

    /**
     * 测试搜索tab长按激活层叠卡片功能
     */
    private fun testSearchTabLongPressStackedCards() {
        try {
            Log.d(TAG, "测试搜索tab长按激活层叠卡片功能")

            // 检查是否在搜索tab
            if (getCurrentTabIndex() != 1) {
                Toast.makeText(this, "请先切换到搜索tab", Toast.LENGTH_SHORT).show()
                return
            }

            // 检查是否有webview卡片
            gestureCardWebViewManager?.let { manager ->
                val allCards = manager.getAllCards()
                if (allCards.isEmpty()) {
                    Toast.makeText(this, "请先打开一些网页，然后再测试", Toast.LENGTH_SHORT).show()
                    return
                }

                Log.d(TAG, "找到 ${allCards.size} 个webview卡片")

                // 模拟长按激活
                activateStackedCardPreview()

                Toast.makeText(this, "层叠卡片预览已激活，可以用手指移动来悬停卡片", Toast.LENGTH_LONG).show()

            } ?: run {
                Toast.makeText(this, "WebView管理器未初始化", Toast.LENGTH_SHORT).show()
            }

        } catch (e: Exception) {
            Log.e(TAG, "测试搜索tab长按功能失败", e)
            Toast.makeText(this, "测试失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 测试新的手势机制
     */
    private fun testNewGestureMechanism() {
        try {
            Log.d(TAG, "🧪 开始测试新的手势机制")

            val message = StringBuilder()
            message.append("🎯 新手势机制测试说明：\n\n")
            message.append("1️⃣ 长按搜索tab图标 → 激活/退出遮罩层\n")
            message.append("2️⃣ 遮罩层中单击搜索tab → 激活多卡片系统\n")
            message.append("3️⃣ 遮罩层中单击其他tab → 退出遮罩层并切换页面\n")
            message.append("4️⃣ 遮罩层中左右滑动 → 切换网页页面\n")
            message.append("5️⃣ 遮罩层中双击 → 关闭当前页面\n\n")

            // 检查当前状态
            if (isSearchTabGestureOverlayActive) {
                message.append("✅ 遮罩层已激活\n")
                message.append("💡 现在可以测试遮罩层内的手势操作")
            } else {
                message.append("⚠️ 遮罩层未激活\n")
                message.append("💡 请长按搜索tab激活遮罩层")
            }

            // 检查网页卡片数量
            val cardCount = gestureCardWebViewManager?.getAllCards()?.size ?: 0
            message.append("\n📊 当前网页卡片数量: $cardCount")

            if (cardCount == 0) {
                message.append("\n💡 建议先打开一些网页进行测试")
            }

            AlertDialog.Builder(this)
                .setTitle("🧪 新手势机制测试")
                .setMessage(message.toString())
                .setPositiveButton("测试长按激活") { _, _ ->
                    // 提示用户长按搜索tab
                    Toast.makeText(this, "请长按搜索tab图标来激活/退出遮罩层", Toast.LENGTH_LONG).show()
                }
                .setNeutralButton("自动激活") { _, _ ->
                    // 如果遮罩层未激活，自动激活
                    if (!isSearchTabGestureOverlayActive) {
                        activateSearchTabGestureOverlay()
                    } else {
                        Toast.makeText(this, "遮罩层已激活，请测试手势操作", Toast.LENGTH_LONG).show()
                    }
                }
                .setNegativeButton("取消", null)
                .show()

            Log.d(TAG, "新手势机制测试对话框已显示")

        } catch (e: Exception) {
            Log.e(TAG, "测试新手势机制失败", e)
            Toast.makeText(this, "测试失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 调试：强制显示层叠卡片预览
     */
    private fun debugShowStackedCards() {
        try {
            Log.d(TAG, "调试：强制显示层叠卡片预览")

            gestureCardWebViewManager?.let { manager ->
                val allCards = manager.getAllCards()
                Log.d(TAG, "调试：找到 ${allCards.size} 个webview卡片")

                if (allCards.isEmpty()) {
                    // 如果没有卡片，创建一些测试卡片
                    Log.d(TAG, "调试：没有卡片，创建测试数据")
                    val testCards = listOf(
                        com.example.aifloatingball.views.StackedCardPreview.WebViewCardData(
                            title = "测试卡片1",
                            url = "https://www.baidu.com",
                            favicon = null,
                            screenshot = null
                        ),
                        com.example.aifloatingball.views.StackedCardPreview.WebViewCardData(
                            title = "测试卡片2",
                            url = "https://www.google.com",
                            favicon = null,
                            screenshot = null
                        ),
                        com.example.aifloatingball.views.StackedCardPreview.WebViewCardData(
                            title = "测试卡片3",
                            url = "https://www.github.com",
                            favicon = null,
                            screenshot = null
                        )
                    )

                    stackedCardPreview?.apply {
                        resetToStackedMode()
                        setWebViewCards(testCards)
                        enableStackedInteraction()
                        visibility = View.VISIBLE
                    }

                    Toast.makeText(this, "调试：已显示3张测试卡片", Toast.LENGTH_LONG).show()
                } else {
                    // 使用真实卡片
                    activateStackedCardPreview()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "调试显示层叠卡片失败", e)
            Toast.makeText(this, "调试失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 显示简单的卡片预览对话框（降级方案）
     */
    private fun showSimpleCardPreviewDialog(
        manager: GestureCardWebViewManager? = gestureCardWebViewManager,
        totalPages: Int = manager?.getAllCards()?.size ?: 0,
        currentIndex: Int = manager?.getAllCards()?.indexOf(manager.getCurrentCard()) ?: 0
    ) {
        try {
            if (manager == null || totalPages == 0) {
                Toast.makeText(this, "没有打开的页面", Toast.LENGTH_SHORT).show()
                return
            }

            val allCards = manager.getAllCards()

            // 获取所有页面信息
            val pageItems = mutableListOf<String>()
            for (i in allCards.indices) {
                val card = allCards[i]
                val title = card.webView.title ?: "页面 ${i + 1}"
                val url = card.webView.url ?: "未知地址"
                val status = if (i == currentIndex) " [当前]" else ""
                pageItems.add("${i + 1}. $title$status\n$url")
            }

            // 创建选择对话框
            AlertDialog.Builder(this, R.style.Theme_MaterialDialog)
                .setTitle("页面卡片预览 ($totalPages 个页面)")
                .setItems(pageItems.toTypedArray()) { _, which ->
                    // 切换到选中的页面
                    manager.switchToCard(which)
                    showPageSwitchAnimation("切换到", which + 1, totalPages)
                }
                .setNegativeButton("关闭", null)
                .show()

            Log.d(TAG, "显示简单卡片预览对话框，共 $totalPages 个页面")
        } catch (e: Exception) {
            Log.e(TAG, "显示简单卡片预览对话框失败", e)
            Toast.makeText(this, "无法显示页面预览", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 卡片预览适配器（简化版，不再使用）
     */
    private inner class CardPreviewAdapter(
        private val manager: GestureCardWebViewManager,
        private val currentIndex: Int,
        private val onItemClick: (Int) -> Unit
    ) : androidx.recyclerview.widget.RecyclerView.Adapter<CardPreviewAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {
            // 使用系统布局，不需要自定义字段
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            // 创建简单的卡片项布局
            val view = LayoutInflater.from(parent.context).inflate(
                android.R.layout.simple_list_item_2, parent, false
            )
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            try {
                val allCards = manager.getAllCards()
                val card = allCards.getOrNull(position)
                val title = card?.webView?.title ?: "页面 ${position + 1}"
                val url = card?.webView?.url ?: "未知地址"

                // 使用系统布局的文本视图
                val text1 = holder.itemView.findViewById<TextView>(android.R.id.text1)
                val text2 = holder.itemView.findViewById<TextView>(android.R.id.text2)

                text1?.text = "${position + 1}. $title${if (position == currentIndex) " [当前]" else ""}"
                text2?.text = url

                // 设置当前页面的背景色
                if (position == currentIndex) {
                    holder.itemView.setBackgroundColor(
                        ContextCompat.getColor(this@SimpleModeActivity, R.color.simple_mode_accent_light)
                    )
                    text1?.setTextColor(android.graphics.Color.WHITE)
                    text2?.setTextColor(android.graphics.Color.WHITE)
                } else {
                    holder.itemView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    text1?.setTextColor(ContextCompat.getColor(this@SimpleModeActivity, R.color.simple_mode_text_primary_light))
                    text2?.setTextColor(ContextCompat.getColor(this@SimpleModeActivity, R.color.simple_mode_text_secondary_light))
                }

                // 设置点击监听
                holder.itemView.setOnClickListener {
                    onItemClick(position)
                }
            } catch (e: Exception) {
                Log.e(TAG, "绑定卡片预览项失败", e)
            }
        }

        override fun getItemCount(): Int = manager.getAllCards().size
    }
    
    // ==================== GroupChatDataChangeListener 实现 ====================
    
    override fun onGroupChatCreated(groupChat: GroupChat) {
        try {
            Log.d(TAG, "收到群聊创建事件: ${groupChat.name}")
            
            // 在主线程中处理UI更新
            runOnUiThread {
                refreshCurrentTabDisplay()
            }
        } catch (e: Exception) {
            Log.e(TAG, "处理群聊创建事件失败", e)
        }
    }
    
    override fun onGroupChatDeleted(groupId: String) {
        try {
            Log.d(TAG, "收到群聊删除事件: $groupId")
            
            // 在主线程中处理UI更新
            runOnUiThread {
                refreshCurrentTabDisplay()
            }
        } catch (e: Exception) {
            Log.e(TAG, "处理群聊删除事件失败", e)
        }
    }
    
    override fun onGroupChatUpdated(groupChat: GroupChat) {
        try {
            Log.d(TAG, "收到群聊更新事件: ${groupChat.name}")
            
            // 在主线程中处理UI更新
            runOnUiThread {
                refreshCurrentTabDisplay()
            }
        } catch (e: Exception) {
            Log.e(TAG, "处理群聊更新事件失败", e)
        }
    }
    
    override fun onGroupChatsReloaded(groupChats: List<GroupChat>) {
        try {
            Log.d(TAG, "收到群聊重新加载事件，共 ${groupChats.size} 个群聊")
            
            // 在主线程中处理UI更新
            runOnUiThread {
                refreshCurrentTabDisplay()
            }
        } catch (e: Exception) {
            Log.e(TAG, "处理群聊重新加载事件失败", e)
        }
    }
    
    /**
     * 刷新当前标签页显示
     * 基于事件驱动模式重构的刷新逻辑
     */
    private fun refreshCurrentTabDisplay() {
        try {
            val chatTabLayout = findViewById<com.google.android.material.tabs.TabLayout>(R.id.chat_tab_layout)
            val currentTab = chatTabLayout?.getTabAt(chatTabLayout.selectedTabPosition)
            val currentTabText = currentTab?.text?.toString()
            
            Log.d(TAG, "刷新当前标签页显示: $currentTabText")
            
            when (currentTabText) {
                "全部" -> {
                    showAllUserAIContacts()
                }
                "AI助手" -> {
                    showAIAssistantGroup()
                }
                else -> {
                    if (currentTabText != null && currentTabText != "+") {
                        showCustomGroupContacts(currentTabText)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "刷新当前标签页显示失败", e)
        }
    }

    // ==================== 搜索tab手势遮罩区功能 ====================

    /**
     * 激活搜索tab手势遮罩区
     * 在整个tab区域创建一个模糊遮罩层，支持手势操作和穿透点击
     */
    private fun activateSearchTabGestureOverlay() {
        try {
            // 如果已经激活，直接返回
            if (isSearchTabGestureOverlayActive) {
                Log.d(TAG, "搜索tab手势遮罩区已激活，跳过重复激活")
                return
            }

            Log.d(TAG, "激活搜索tab手势遮罩区")

            // 显示激活提示
            showMaterialToast("🎯 长按搜索tab激活遮罩层成功！现在可以使用手势操作")

            // 只在第一次进入时显示详细操作说明
            if (!hasShownGestureInstructions) {
                handler.postDelayed({
                    showGestureInstructions()
                    hasShownGestureInstructions = true // 标记已显示过
                }, 1500)
            }

            // 获取底部导航栏容器
            val bottomNavigation = findViewById<LinearLayout>(R.id.bottom_navigation)
            if (bottomNavigation == null) {
                Log.e(TAG, "底部导航栏未找到，无法创建手势遮罩区")
                return
            }

            // 创建手势检测器 - 网页浏览手势操作
            // 创建手势监听器
            val gestureListener = object : GestureDetector.SimpleOnGestureListener(), GestureDetector.OnDoubleTapListener {
                override fun onDown(e: MotionEvent): Boolean {
                    // 重置手势状态
                    isLongPressDetected = false
                    isDoubleTapDetected = false
                    Log.d(TAG, "手势开始: x=${e.x}, y=${e.y}")
                    return true // 返回true表示我们想要处理后续事件
                }

                override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                    Log.d(TAG, "检测到滑动手势: velocityX=$velocityX, velocityY=$velocityY")
                    // 如果已经检测到长按或双击，不处理滑动
                    if (isLongPressDetected || isDoubleTapDetected) {
                        Log.d(TAG, "已检测到其他手势，跳过滑动处理")
                        return false
                    }
                    // 网页浏览手势操作
                    val handled = handleWebBrowsingGesture(e1, e2, velocityX, velocityY)
                    Log.d(TAG, "滑动手势处理结果: $handled")
                    return handled // 只有成功处理时才消费事件
                }

                override fun onLongPress(e: MotionEvent) {
                    Log.d(TAG, "检测到长按手势，但不处理以避免与搜索tab长按冲突")
                    // 移除长按刷新功能，让长按事件穿透到搜索tab处理退出操作
                    // 这样用户可以通过长按搜索tab来退出遮罩层
                    isLongPressDetected = false // 不标记为已处理，让事件穿透
                }

                override fun onDoubleTap(e: MotionEvent): Boolean {
                    Log.d(TAG, "检测到双击手势")
                    isDoubleTapDetected = true
                    // 双击手势 - 关闭当前页面
                    handleDoubleTapGesture(e)
                    return true // 消费双击事件
                }

                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    // 单击确认 - 检查是否点击tab区域
                    Log.d(TAG, "遮罩区单击确认: x=${e.x}, y=${e.y}")

                    // 检测点击的tab区域
                    val bottomNavigation = findViewById<LinearLayout>(R.id.bottom_navigation)
                    if (bottomNavigation != null) {
                        val location = IntArray(2)
                        bottomNavigation.getLocationOnScreen(location)
                        val relativeX = e.rawX - location[0]
                        val tabWidth = bottomNavigation.width / 6
                        val tabIndex = (relativeX / tabWidth).toInt()

                        Log.d(TAG, "遮罩层中单击tab，tabIndex=$tabIndex")

                        when (tabIndex) {
                            0 -> {
                                // 单击对话tab - 退出遮罩层并进入对话页面
                                Log.d(TAG, "遮罩层中单击对话tab，退出遮罩层并进入对话页面")
                                deactivateSearchTabGestureOverlay()
                                showChat()
                                showMaterialToast("💬 已切换到对话页面")
                                return true // 消费事件
                            }
                            1 -> {
                                // 在遮罩层中单击搜索tab - 激活多卡片系统
                                Log.d(TAG, "遮罩层中单击搜索tab，激活多卡片系统")
                                activateStackedCardPreview()
                                showMaterialToast("📱 多卡片系统已激活")
                                return true // 消费事件
                            }
                            2 -> {
                                // 单击任务tab - 退出遮罩层并进入任务页面
                                Log.d(TAG, "遮罩层中单击任务tab，退出遮罩层并进入任务页面")
                                deactivateSearchTabGestureOverlay()
                                showTaskSelection()
                                showMaterialToast("📋 已切换到任务页面")
                                return true // 消费事件
                            }
                            3 -> {
                                // 单击语音tab - 检查语音tab是否可见
                                val voiceTab = findViewById<LinearLayout>(R.id.tab_voice)
                                if (voiceTab?.visibility == View.VISIBLE) {
                                    // 语音tab可见，正常切换
                                    Log.d(TAG, "遮罩层中单击语音tab，退出遮罩层并进入语音页面")
                                    deactivateSearchTabGestureOverlay()
                                    showVoice()
                                    showMaterialToast("🎤 已切换到语音页面")
                                    return true // 消费事件
                                } else {
                                    // 语音tab被隐藏，不处理此点击
                                    Log.d(TAG, "语音tab已隐藏，忽略点击事件")
                                    return false // 不消费事件
                                }
                            }
                            4 -> {
                                // 单击软件tab - 退出遮罩层并进入软件页面
                                Log.d(TAG, "遮罩层中单击软件tab，退出遮罩层并进入软件页面")
                                deactivateSearchTabGestureOverlay()
                                showAppSearch()
                                showMaterialToast("📱 已切换到软件页面")
                                return true // 消费事件
                            }
                            5 -> {
                                // 单击设置tab - 退出遮罩层并进入设置页面
                                Log.d(TAG, "遮罩层中单击设置tab，退出遮罩层并进入设置页面")
                                deactivateSearchTabGestureOverlay()
                                showSettings()
                                showMaterialToast("⚙️ 已切换到设置页面")
                                return true // 消费事件
                            }
                        }
                    }

                    return false // 不消费事件，保持穿透
                }

                // DoubleTapListener 方法
                override fun onDoubleTapEvent(e: MotionEvent): Boolean {
                    Log.d(TAG, "双击事件: action=${e.action}")
                    return false
                }

                override fun onSingleTapUp(e: MotionEvent): Boolean {
                    Log.d(TAG, "单击抬起: x=${e.x}, y=${e.y}")
                    return false // 不消费，让双击检测器处理
                }
            }

            gestureDetectorForOverlay = GestureDetectorCompat(this, gestureListener)
            // 启用双击检测
            gestureDetectorForOverlay?.setOnDoubleTapListener(gestureListener)
            // 确保长按检测启用
            gestureDetectorForOverlay?.setIsLongpressEnabled(true)
            Log.d(TAG, "手势检测器配置完成，双击和长按检测已启用")

            // 创建手势遮罩层
            searchTabGestureOverlay = FrameLayout(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    bottomNavigation.height
                ).apply {
                    gravity = android.view.Gravity.BOTTOM
                }

                // 设置模糊背景和绿色边框
                background = createOverlayBackground()

                // 关键：设置为不可点击，但可以接收触摸事件用于检测
                isClickable = false
                isFocusable = false
                isFocusableInTouchMode = false

                // 设置触摸监听器 - 处理手势检测和穿透
                setOnTouchListener { view, event ->
                    try {
                        // 记录遮罩层接收到的触摸事件
                        Log.d(TAG, "遮罩层接收到触摸事件: action=${event.action}, x=${event.x}, y=${event.y}")

                        // 检测点击的tab区域
                        var isSearchTabTouch = false
                        if (event.action == MotionEvent.ACTION_DOWN) {
                            val bottomNavigation = findViewById<LinearLayout>(R.id.bottom_navigation)
                            if (bottomNavigation != null) {
                                val location = IntArray(2)
                                bottomNavigation.getLocationOnScreen(location)
                                val relativeX = event.rawX - location[0]
                                val tabWidth = bottomNavigation.width / 6
                                val tabIndex = (relativeX / tabWidth).toInt()

                                Log.d(TAG, "遮罩层检测到tab触摸: tabIndex=$tabIndex, relativeX=$relativeX")

                                when (tabIndex) {
                                    1 -> {
                                        // 触摸搜索tab - 可能是单击或长按
                                        isSearchTabTouch = true
                                        Log.d(TAG, "遮罩层检测到搜索tab触摸，将穿透处理")
                                    }
                                    in 0..5 -> {
                                        // 触摸其他tab - 将由手势检测器处理单击事件
                                        Log.d(TAG, "遮罩层检测到其他tab触摸，将由手势检测器处理")
                                    }
                                }
                            }
                        }

                        // 如果是搜索tab的触摸，让事件直接穿透，不进行手势检测
                        if (isSearchTabTouch) {
                            Log.d(TAG, "搜索tab触摸，直接穿透到底层处理长按/单击")
                            return@setOnTouchListener false // 直接穿透
                        }

                        // 让手势检测器处理手势，获取处理结果
                        val gestureHandled = gestureDetectorForOverlay?.onTouchEvent(event) ?: false
                        Log.d(TAG, "手势检测器处理结果: $gestureHandled")

                        // 检查是否有手势被识别（双击，但不包括长按）
                        // 长按事件需要穿透到搜索tab处理退出操作
                        val anyGestureDetected = gestureHandled || isDoubleTapDetected
                        Log.d(TAG, "手势状态: gestureHandled=$gestureHandled, longPress=$isLongPressDetected(穿透), doubleTap=$isDoubleTapDetected")

                        // 如果有手势被处理了，消费事件；否则穿透到底层
                        if (anyGestureDetected) {
                            Log.d(TAG, "手势被处理，消费事件")
                            true // 消费事件
                        } else {
                            Log.d(TAG, "手势未被处理，穿透事件")
                            false // 穿透到底层
                        }

                    } catch (e: Exception) {
                        Log.e(TAG, "遮罩层触摸处理失败", e)
                        false
                    }
                }
            }

            // 将遮罩层添加到根布局
            val rootLayout = findViewById<FrameLayout>(android.R.id.content)
            rootLayout.addView(searchTabGestureOverlay)

            isSearchTabGestureOverlayActive = true
            Log.d(TAG, "搜索tab手势遮罩区激活成功")

        } catch (e: Exception) {
            Log.e(TAG, "激活搜索tab手势遮罩区失败", e)
        }
    }

    /**
     * 退出搜索tab手势遮罩区
     */
    private fun deactivateSearchTabGestureOverlay() {
        try {
            if (!isSearchTabGestureOverlayActive) {
                return
            }

            Log.d(TAG, "退出搜索tab手势遮罩区")

            // 显示退出提示
            showMaterialToast("👋 长按搜索tab退出遮罩层成功！")

            // 移除遮罩层
            searchTabGestureOverlay?.let { overlay ->
                val rootLayout = findViewById<FrameLayout>(android.R.id.content)
                rootLayout.removeView(overlay)
            }

            searchTabGestureOverlay = null
            gestureDetectorForOverlay = null
            isSearchTabGestureOverlayActive = false

            // 重置手势状态
            isLongPressDetected = false
            isDoubleTapDetected = false

            Log.d(TAG, "搜索tab手势遮罩区已退出")

        } catch (e: Exception) {
            Log.e(TAG, "退出搜索tab手势遮罩区失败", e)
        }
    }

    /**
     * 显示手势操作说明
     */
    private fun showGestureInstructions() {
        try {
            val instructions = """
                🎮 遮罩层手势操作说明：

                📱 单击搜索tab → 激活多卡片系统
                💬 单击对话tab → 退出遮罩层并进入对话页面
                📋 单击任务tab → 退出遮罩层并进入任务页面
                🎤 单击语音tab → 退出遮罩层并进入语音页面
                📱 单击软件tab → 退出遮罩层并进入软件页面
                ⚙️ 单击设置tab → 退出遮罩层并进入设置页面
                ↔️ 左右滑动 → 切换网页页面
                👆👆 双击遮罩层 → 关闭当前页面
                🚪 长按搜索tab → 退出遮罩层

                💡 提示：单击任意tab都可快速切换页面
            """.trimIndent()

            AlertDialog.Builder(this)
                .setTitle("🎯 遮罩层已激活")
                .setMessage(instructions)
                .setPositiveButton("开始使用", null)
                .setNegativeButton("退出遮罩层") { _, _ ->
                    deactivateSearchTabGestureOverlay()
                }
                .show()

        } catch (e: Exception) {
            Log.e(TAG, "显示手势说明失败", e)
        }
    }

    /**
     * 处理搜索tab手势遮罩区的触摸事件（简化版本，主要逻辑已移到触摸监听器中）
     */
    private fun handleSearchTabGestureOverlayTouch(event: MotionEvent) {
        // 这个方法现在主要用于兼容性，实际处理逻辑在触摸监听器中
        Log.d(TAG, "handleSearchTabGestureOverlayTouch called: x=${event.x}, y=${event.y}")
    }

    /**
     * 处理网页浏览手势操作
     * @return true 如果手势被成功处理，false 如果手势未被识别
     */
    private fun handleWebBrowsingGesture(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
        try {
            if (e1 == null) return false

            val deltaX = e2.x - e1.x
            val deltaY = e2.y - e1.y
            val absVelocityX = kotlin.math.abs(velocityX)
            val absVelocityY = kotlin.math.abs(velocityY)
            val absDeltaX = kotlin.math.abs(deltaX)
            val absDeltaY = kotlin.math.abs(deltaY)

            Log.d(TAG, "网页浏览手势分析: deltaX=$deltaX, deltaY=$deltaY, velocityX=$velocityX, velocityY=$velocityY")
            Log.d(TAG, "速度分析: absVelocityX=$absVelocityX, absVelocityY=$absVelocityY")
            Log.d(TAG, "距离分析: absDeltaX=$absDeltaX, absDeltaY=$absDeltaY")

            // 判断手势方向和速度（优化识别逻辑）
            when {
                // 水平滑动 - 页面卡片切换
                // 条件：水平距离大于垂直距离 且 (水平速度大于阈值 或 水平距离足够大)
                absDeltaX > absDeltaY && (absVelocityX > 300 || absDeltaX > 80) -> {
                    Log.d(TAG, "检测到水平滑动手势")
                    if (deltaX > 30) { // 降低距离要求
                        // 向右滑动 - 切换到下一个页面卡片
                        Log.d(TAG, "向右滑动")
                        handleNextPageCardGesture()
                        return true
                    } else if (deltaX < -30) {
                        // 向左滑动 - 切换到上一个页面卡片
                        Log.d(TAG, "向左滑动")
                        handlePreviousPageCardGesture()
                        return true
                    } else {
                        Log.d(TAG, "滑动距离不足: deltaX=$deltaX")
                        return false
                    }
                }
                else -> {
                    Log.d(TAG, "未识别的手势或不符合水平滑动条件")
                    return false
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "处理网页浏览手势失败", e)
            return false
        }
    }

    /**
     * 处理长按手势 - 刷新当前页面
     */
    private fun handleLongPressGesture(e: MotionEvent) {
        try {
            Log.d(TAG, "遮罩区长按手势 - 刷新当前页面")

            // 刷新当前页面
            refreshCurrentWebPage()

            // 显示Material风格动效
            showGestureAnimation(e.x, e.y, "刷新", R.drawable.ic_refresh, android.graphics.Color.GREEN)

            // Toast提示
            showMaterialToast("页面已刷新", android.graphics.Color.GREEN)

        } catch (e: Exception) {
            Log.e(TAG, "处理长按手势失败", e)
        }
    }

    /**
     * 处理双击手势 - 关闭当前页面
     */
    private fun handleDoubleTapGesture(e: MotionEvent) {
        try {
            Log.d(TAG, "遮罩区双击手势 - 关闭当前页面")

            // 关闭当前页面
            closeCurrentWebPage()

            // 显示Material风格动效
            showGestureAnimation(e.x, e.y, "关闭", R.drawable.ic_close, android.graphics.Color.GREEN)

            // Toast提示
            showMaterialToast("页面已关闭", android.graphics.Color.GREEN)

        } catch (e: Exception) {
            Log.e(TAG, "处理双击手势失败", e)
        }
    }

    /**
     * 处理下一个页面卡片手势
     */
    private fun handleNextPageCardGesture() {
        try {
            var handled = false

            // 优先检查MobileCardManager
            val mobileCards = mobileCardManager?.getAllCards()
            if (!mobileCards.isNullOrEmpty() && mobileCards.size > 1) {
                val currentCard = mobileCardManager?.getCurrentCard()
                val currentIndex = mobileCards.indexOf(currentCard)
                val nextIndex = if (currentIndex >= 0 && currentIndex < mobileCards.size - 1) {
                    currentIndex + 1
                } else {
                    0 // 循环到第一个
                }

                mobileCardManager?.switchToCard(nextIndex)
                val nextCard = mobileCards[nextIndex]
                showMaterialToast("切换到: ${nextCard.title ?: "下一页"}", android.graphics.Color.GREEN)
                // 显示滑动动画效果
                showSwipeAnimation(true) // true表示向右滑动
                handled = true
                Log.d(TAG, "手势遮罩区：手机卡片切换到下一个页面")
            }

            // 如果MobileCardManager没有处理，检查GestureCardWebViewManager
            if (!handled) {
                val gestureCards = gestureCardWebViewManager?.getAllCards()
                if (!gestureCards.isNullOrEmpty() && gestureCards.size > 1) {
                    val currentCard = gestureCardWebViewManager?.getCurrentCard()
                    val currentIndex = gestureCards.indexOf(currentCard)
                    val nextIndex = if (currentIndex >= 0 && currentIndex < gestureCards.size - 1) {
                        currentIndex + 1
                    } else {
                        0 // 循环到第一个
                    }

                    gestureCardWebViewManager?.switchToCard(nextIndex)
                    val nextCard = gestureCards[nextIndex]
                    showMaterialToast("切换到: ${nextCard.title ?: "下一页"}", android.graphics.Color.GREEN)
                    // 显示滑动动画效果
                    showSwipeAnimation(true) // true表示向右滑动
                    handled = true
                    Log.d(TAG, "手势遮罩区：手势卡片切换到下一个页面")
                }
            }

            if (!handled) {
                showMaterialToast("没有其他页面", android.graphics.Color.GREEN)
                Log.d(TAG, "手势遮罩区：没有其他页面可切换")
            }

        } catch (e: Exception) {
            Log.e(TAG, "处理下一个页面卡片手势失败", e)
        }
    }

    /**
     * 处理上一个页面卡片手势
     */
    private fun handlePreviousPageCardGesture() {
        try {
            var handled = false

            // 优先检查MobileCardManager
            val mobileCards = mobileCardManager?.getAllCards()
            if (!mobileCards.isNullOrEmpty() && mobileCards.size > 1) {
                val currentCard = mobileCardManager?.getCurrentCard()
                val currentIndex = mobileCards.indexOf(currentCard)
                val prevIndex = if (currentIndex > 0) {
                    currentIndex - 1
                } else {
                    mobileCards.size - 1 // 循环到最后一个
                }

                mobileCardManager?.switchToCard(prevIndex)
                val prevCard = mobileCards[prevIndex]
                showMaterialToast("切换到: ${prevCard.title ?: "上一页"}", android.graphics.Color.GREEN)
                // 显示滑动动画效果
                showSwipeAnimation(false) // false表示向左滑动
                handled = true
                Log.d(TAG, "手势遮罩区：手机卡片切换到上一个页面")
            }

            // 如果MobileCardManager没有处理，检查GestureCardWebViewManager
            if (!handled) {
                val gestureCards = gestureCardWebViewManager?.getAllCards()
                if (!gestureCards.isNullOrEmpty() && gestureCards.size > 1) {
                    val currentCard = gestureCardWebViewManager?.getCurrentCard()
                    val currentIndex = gestureCards.indexOf(currentCard)
                    val prevIndex = if (currentIndex > 0) {
                        currentIndex - 1
                    } else {
                        gestureCards.size - 1 // 循环到最后一个
                    }

                    gestureCardWebViewManager?.switchToCard(prevIndex)
                    val prevCard = gestureCards[prevIndex]
                    showMaterialToast("切换到: ${prevCard.title ?: "上一页"}", android.graphics.Color.GREEN)
                    // 显示滑动动画效果
                    showSwipeAnimation(false) // false表示向左滑动
                    handled = true
                    Log.d(TAG, "手势遮罩区：手势卡片切换到上一个页面")
                }
            }

            if (!handled) {
                showMaterialToast("没有其他页面", android.graphics.Color.GREEN)
                Log.d(TAG, "手势遮罩区：没有其他页面可切换")
            }

        } catch (e: Exception) {
            Log.e(TAG, "处理上一个页面卡片手势失败", e)
        }
    }

    /**
     * 关闭当前网页页面
     */
    private fun closeCurrentWebPage() {
        try {
            var handled = false

            // 优先检查MobileCardManager
            val mobileCards = mobileCardManager?.getAllCards()
            if (!mobileCards.isNullOrEmpty()) {
                val currentCard = mobileCardManager?.getCurrentCard()
                val currentIndex = mobileCards.indexOf(currentCard)
                if (currentIndex >= 0) {
                    mobileCardManager?.removeCard(currentIndex)
                    handled = true
                    Log.d(TAG, "手势遮罩区：关闭手机卡片页面")
                }
            }

            // 如果MobileCardManager没有处理，检查GestureCardWebViewManager
            if (!handled) {
                val gestureCards = gestureCardWebViewManager?.getAllCards()
                if (!gestureCards.isNullOrEmpty()) {
                    val currentCard = gestureCardWebViewManager?.getCurrentCard()
                    val currentIndex = gestureCards.indexOf(currentCard)
                    if (currentIndex >= 0) {
                        gestureCardWebViewManager?.removeCard(currentIndex)
                        handled = true
                        Log.d(TAG, "手势遮罩区：关闭手势卡片页面")
                    }
                }
            }

            if (!handled) {
                showMaterialToast("没有可关闭的页面", android.graphics.Color.GREEN)
                Log.d(TAG, "手势遮罩区：没有可关闭的页面")
            }

        } catch (e: Exception) {
            Log.e(TAG, "关闭当前网页页面失败", e)
        }
    }



    /**
     * 刷新当前网页
     */
    private fun refreshCurrentWebPage() {
        try {
            var handled = false

            // 优先检查MobileCardManager
            val mobileCurrentCard = mobileCardManager?.getCurrentCard()
            if (mobileCurrentCard?.webView != null) {
                mobileCurrentCard.webView.reload()
                handled = true
                Log.d(TAG, "手势遮罩区：手机卡片页面已刷新")
            }

            // 如果MobileCardManager没有处理，检查GestureCardWebViewManager
            if (!handled) {
                val gestureCurrentCard = gestureCardWebViewManager?.getCurrentCard()
                if (gestureCurrentCard?.webView != null) {
                    gestureCurrentCard.webView.reload()
                    handled = true
                    Log.d(TAG, "手势遮罩区：手势卡片页面已刷新")
                }
            }

            if (!handled) {
                showMaterialToast("没有可刷新的页面", android.graphics.Color.GREEN)
                Log.d(TAG, "手势遮罩区：没有可刷新的页面")
            }

        } catch (e: Exception) {
            Log.e(TAG, "刷新当前网页失败", e)
        }
    }

    /**
     * 创建新网页（一次只能创建一个）
     */
    private fun createNewWebPage() {
        try {
            var created = false

            // 检查是否已经有多个页面，如果有则不创建新页面
            val mobileCards = mobileCardManager?.getAllCards()
            val gestureCards = gestureCardWebViewManager?.getAllCards()

            val totalCards = (mobileCards?.size ?: 0) + (gestureCards?.size ?: 0)
            if (totalCards >= 5) { // 限制最多5个页面
                showMaterialToast("页面数量已达上限", android.graphics.Color.GREEN)
                Log.d(TAG, "页面数量已达上限，无法创建新页面")
                return
            }

            // 优先使用MobileCardManager
            if (mobileCardManager != null) {
                val newCard = mobileCardManager?.addNewCard("about:blank")
                if (newCard != null) {
                    created = true
                    showMaterialToast("已打开新页面", android.graphics.Color.GREEN)
                    Log.d(TAG, "使用MobileCardManager创建新页面")
                }
            }

            // 如果MobileCardManager不可用，使用GestureCardWebViewManager
            if (!created && gestureCardWebViewManager != null) {
                val newCard = gestureCardWebViewManager?.addNewCard("about:blank")
                if (newCard != null) {
                    created = true
                    showMaterialToast("已打开新页面", android.graphics.Color.GREEN)
                    Log.d(TAG, "使用GestureCardWebViewManager创建新页面")
                }
            }

            if (!created) {
                showMaterialToast("无法创建新页面", android.graphics.Color.GREEN)
                Log.w(TAG, "创建新页面失败")
            }

        } catch (e: Exception) {
            Log.e(TAG, "创建新网页失败", e)
            showMaterialToast("创建页面失败", android.graphics.Color.GREEN)
        }
    }

    /**
     * 执行AI搜索
     */
    private fun performAISearch(query: String) {
        try {
            Log.d(TAG, "执行AI搜索: $query")

            // 使用默认AI搜索引擎进行搜索
            val aiSearchUrl = "https://www.perplexity.ai/search?q=${java.net.URLEncoder.encode(query, "UTF-8")}"

            // 获取当前WebView
            val currentCard = gestureCardWebViewManager?.getCurrentCard() ?: mobileCardManager?.getCurrentCard()
            if (currentCard?.webView != null) {
                currentCard.webView.loadUrl(aiSearchUrl)
                showMaterialToast("🤖 AI搜索: $query")
            } else {
                // 如果没有当前WebView，创建新的
                createNewWebPage()
                // 等待WebView创建完成后加载URL
                Handler(Looper.getMainLooper()).postDelayed({
                    val newCard = gestureCardWebViewManager?.getCurrentCard() ?: mobileCardManager?.getCurrentCard()
                    newCard?.webView?.loadUrl(aiSearchUrl)
                }, 100)
                showMaterialToast("🤖 新建AI搜索页面: $query")
            }

            // 清空搜索框
            browserSearchInput.setText("")

        } catch (e: Exception) {
            Log.e(TAG, "AI搜索失败", e)
            showMaterialToast("❌ AI搜索失败，请重试")
        }
    }

    /**
     * 打开AI助手
     */
    private fun openAIAssistant() {
        try {
            Log.d(TAG, "打开AI助手")

            // 切换到对话tab，激活AI助手功能
            showChat()
            showMaterialToast("🤖 AI助手已激活")

        } catch (e: Exception) {
            Log.e(TAG, "打开AI助手失败", e)
            showMaterialToast("❌ 无法打开AI助手")
        }
    }

    /**
     * 显示自定义样式的Toast提示 - 支持暗色/亮色模式，绿色边框，显示在页面正上方
     */
    private fun showMaterialToast(message: String, color: Int = android.graphics.Color.GREEN) {
        try {
            runOnUiThread {
                // 检测当前主题模式
                val isDarkMode = (resources.configuration.uiMode and
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                    android.content.res.Configuration.UI_MODE_NIGHT_YES

                // 创建自定义Toast布局
                val layout = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(32, 20, 32, 20)
                    background = GradientDrawable().apply {
                        // 根据主题设置背景色
                        val backgroundColor = if (isDarkMode) {
                            android.graphics.Color.parseColor("#2D2D2D") // 暗色模式：深灰色背景
                        } else {
                            android.graphics.Color.parseColor("#FFFFFF") // 亮色模式：白色背景
                        }
                        setColor(backgroundColor)
                        setStroke(6, android.graphics.Color.parseColor("#4CAF50")) // 绿色边框
                        cornerRadius = 28f
                    }
                    elevation = 12f
                }

                // 添加图标
                val icon = ImageView(this).apply {
                    setImageResource(android.R.drawable.ic_dialog_info)
                    setColorFilter(android.graphics.Color.parseColor("#4CAF50")) // 绿色图标
                    layoutParams = LinearLayout.LayoutParams(56, 56).apply {
                        setMargins(0, 0, 20, 0)
                        gravity = android.view.Gravity.CENTER_VERTICAL
                    }
                }

                // 添加文本
                val textView = TextView(this).apply {
                    text = message
                    // 根据主题设置文字颜色
                    val textColor = if (isDarkMode) {
                        android.graphics.Color.parseColor("#FFFFFF") // 暗色模式：白色文字
                    } else {
                        android.graphics.Color.parseColor("#2E7D32") // 亮色模式：深绿色文字
                    }
                    setTextColor(textColor)
                    textSize = 16f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        gravity = android.view.Gravity.CENTER_VERTICAL
                    }
                }

                layout.addView(icon)
                layout.addView(textView)

                // 创建并显示Toast - 显示在页面正上方
                val toast = Toast(this).apply {
                    view = layout
                    duration = Toast.LENGTH_SHORT
                    // 设置位置：页面正上方，距离顶部200px
                    setGravity(android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL, 0, 200)
                }

                toast.show()
                Log.d(TAG, "显示自定义Toast: $message (${if (isDarkMode) "暗色" else "亮色"}模式)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "显示自定义Toast失败", e)
            // 回退到普通Toast
            runOnUiThread {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).apply {
                    setGravity(android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL, 0, 200)
                }.show()
            }
        }
    }

    /**
     * 显示手势动画效果 - Material Design风格
     */
    private fun showGestureAnimation(x: Float, y: Float, actionText: String, iconRes: Int, color: Int) {
        try {
            runOnUiThread {
                // 创建动画容器
                val animationContainer = FrameLayout(this)

                // 创建背景圆圈
                val backgroundCircle = android.view.View(this)
                val circleDrawable = android.graphics.drawable.GradientDrawable()
                circleDrawable.shape = android.graphics.drawable.GradientDrawable.OVAL
                circleDrawable.setColor(android.graphics.Color.argb(80, 0, 255, 0)) // 半透明绿色
                backgroundCircle.background = circleDrawable

                // 创建图标视图
                val iconView = ImageView(this)
                iconView.setImageResource(android.R.drawable.ic_menu_rotate) // 使用系统图标作为占位符
                iconView.setColorFilter(color)

                // 设置布局参数
                val circleSize = 120
                val iconSize = 48

                val circleParams = FrameLayout.LayoutParams(circleSize, circleSize)
                circleParams.gravity = android.view.Gravity.CENTER
                backgroundCircle.layoutParams = circleParams

                val iconParams = FrameLayout.LayoutParams(iconSize, iconSize)
                iconParams.gravity = android.view.Gravity.CENTER
                iconView.layoutParams = iconParams

                // 添加视图到容器
                animationContainer.addView(backgroundCircle)
                animationContainer.addView(iconView)

                // 设置容器位置
                val containerParams = FrameLayout.LayoutParams(circleSize, circleSize)
                animationContainer.layoutParams = containerParams
                animationContainer.x = x - circleSize / 2
                animationContainer.y = y - circleSize / 2

                // 设置初始状态
                animationContainer.alpha = 0f
                animationContainer.scaleX = 0.3f
                animationContainer.scaleY = 0.3f

                // 添加到遮罩层
                searchTabGestureOverlay?.addView(animationContainer)

                // Material Design风格动画
                // 第一阶段：快速放大并显示
                animationContainer.animate()
                    .alpha(1f)
                    .scaleX(1.1f)
                    .scaleY(1.1f)
                    .setDuration(150)
                    .setInterpolator(android.view.animation.DecelerateInterpolator())
                    .withEndAction {
                        // 第二阶段：轻微回弹
                        animationContainer.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(100)
                            .setInterpolator(android.view.animation.OvershootInterpolator())
                            .withEndAction {
                                // 第三阶段：保持一段时间后淡出
                                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                    animationContainer.animate()
                                        .alpha(0f)
                                        .scaleX(0.8f)
                                        .scaleY(0.8f)
                                        .setDuration(200)
                                        .setInterpolator(android.view.animation.AccelerateInterpolator())
                                        .withEndAction {
                                            searchTabGestureOverlay?.removeView(animationContainer)
                                        }
                                        .start()
                                }, 500) // 保持500ms
                            }
                            .start()
                    }
                    .start()

                Log.d(TAG, "显示Material风格手势动画: $actionText")
            }
        } catch (e: Exception) {
            Log.e(TAG, "显示手势动画失败", e)
        }
    }

    /**
     * 显示滑动动画效果 - Material Design风格
     */
    private fun showSwipeAnimation(isRightSwipe: Boolean) {
        try {
            runOnUiThread {
                val overlay = searchTabGestureOverlay ?: return@runOnUiThread

                // 创建滑动指示器
                val swipeIndicator = android.view.View(this)
                val indicatorDrawable = android.graphics.drawable.GradientDrawable()
                indicatorDrawable.shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                indicatorDrawable.setColor(android.graphics.Color.argb(120, 0, 255, 0)) // 半透明绿色
                indicatorDrawable.cornerRadius = 8f
                swipeIndicator.background = indicatorDrawable

                // 设置指示器大小和位置
                val indicatorWidth = 100
                val indicatorHeight = 8
                val layoutParams = FrameLayout.LayoutParams(indicatorWidth, indicatorHeight)
                layoutParams.gravity = android.view.Gravity.CENTER
                swipeIndicator.layoutParams = layoutParams

                // 设置初始位置
                val centerX = overlay.width / 2f
                val centerY = overlay.height / 2f
                val startOffset = if (isRightSwipe) -50f else 50f

                swipeIndicator.x = centerX + startOffset - indicatorWidth / 2
                swipeIndicator.y = centerY - indicatorHeight / 2
                swipeIndicator.alpha = 0f

                // 添加到遮罩层
                overlay.addView(swipeIndicator)

                // 执行滑动动画
                val endOffset = if (isRightSwipe) 50f else -50f
                swipeIndicator.animate()
                    .alpha(1f)
                    .setDuration(100)
                    .withEndAction {
                        swipeIndicator.animate()
                            .translationX(endOffset)
                            .setDuration(300)
                            .setInterpolator(android.view.animation.DecelerateInterpolator())
                            .withEndAction {
                                swipeIndicator.animate()
                                    .alpha(0f)
                                    .setDuration(200)
                                    .withEndAction {
                                        overlay.removeView(swipeIndicator)
                                    }
                                    .start()
                            }
                            .start()
                    }
                    .start()

                Log.d(TAG, "显示滑动动画: ${if (isRightSwipe) "向右" else "向左"}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "显示滑动动画失败", e)
        }
    }

    /**
     * 创建遮罩层背景（模糊效果 + 绿色边框）
     */
    private fun createOverlayBackground(): android.graphics.drawable.Drawable {
        return android.graphics.drawable.GradientDrawable().apply {
            setColor(android.graphics.Color.argb(120, 255, 255, 255)) // 半透明白色模糊效果
            setStroke(4, android.graphics.Color.GREEN) // 绿色边框
            cornerRadius = 8f // 圆角
        }
    }

}