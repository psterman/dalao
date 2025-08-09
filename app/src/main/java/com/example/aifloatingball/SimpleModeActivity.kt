package com.example.aifloatingball

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AlertDialog
import com.example.aifloatingball.voice.VoiceInputManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.aifloatingball.manager.ModeManager
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
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

import com.example.aifloatingball.service.SimpleModeService
import com.example.aifloatingball.service.FloatingWindowService
import com.example.aifloatingball.service.DynamicIslandService
import com.example.aifloatingball.voice.VoicePromptBranchManager
import com.example.aifloatingball.webview.MultiPageWebViewManager
import com.example.aifloatingball.webview.CardWebViewManager
import com.example.aifloatingball.webview.GestureCardWebViewManager
import com.example.aifloatingball.webview.MobileCardManager
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

class SimpleModeActivity : AppCompatActivity(), VoicePromptBranchManager.BranchViewListener {

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
        SETTINGS           // 设置页面
    }

    private var currentState = UIState.TASK_SELECTION
    private var currentTemplate: PromptTemplate? = null
    private var currentStepIndex = 0
    private lateinit var userPromptData: UserPromptData
    private lateinit var settingsManager: SettingsManager

    // UI组件
    private lateinit var chatLayout: LinearLayout
    private lateinit var taskSelectionLayout: LinearLayout
    private lateinit var stepGuidanceLayout: LinearLayout
    private lateinit var promptPreviewLayout: LinearLayout
    private lateinit var voiceLayout: ScrollView
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



    // 手势卡片式WebView管理器
    private var gestureCardWebViewManager: GestureCardWebViewManager? = null

    // 新的手机卡片管理器
    private var mobileCardManager: MobileCardManager? = null

    // 四分之一圆弧操作栏
    private var quarterArcOperationBar: QuarterArcOperationBar? = null


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

    // 浏览器功能相关
    private lateinit var browserGestureDetector: GestureDetectorCompat
    private var currentSearchEngine: com.example.aifloatingball.model.SearchEngine? = null



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

        // 检查是否需要恢复之前的状态
        val savedState = savedInstanceState?.getString(KEY_CURRENT_STATE)
        if (savedState != null) {
            Log.d(TAG, "Restoring saved state: $savedState")
            restoreState(savedState)
        } else {
            Log.d(TAG, "No saved state, showing chat")
            showChat()
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
                UIState.SETTINGS -> showSettings()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring state: $stateName", e)
            // 如果恢复失败，回到默认状态
            showChat()
        }
    }

    private fun checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val hasPermission = Settings.canDrawOverlays(this)
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
            // 所以可以使用固定的索引映射
            val isSelected = when (i) {
                0 -> currentState == UIState.CHAT            // 对话
                1 -> currentState == UIState.BROWSER         // 搜索
                2 -> currentState == UIState.TASK_SELECTION  // 任务
                3 -> currentState == UIState.VOICE           // 语音
                4 -> currentState == UIState.SETTINGS        // 设置
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

    private fun initializeViews() {
        // 主要布局
        chatLayout = findViewById(R.id.chat_layout)
        taskSelectionLayout = findViewById(R.id.task_selection_layout)
        stepGuidanceLayout = findViewById(R.id.step_guidance_layout)
        promptPreviewLayout = findViewById(R.id.prompt_preview_layout)
        voiceLayout = findViewById(R.id.voice_layout)
        browserLayout = findViewById(R.id.browser_layout)
        settingsLayout = findViewById(R.id.settings_layout)
        // modeSwitchWidget = findViewById(R.id.mode_switch_widget)  // 暂时禁用

        // 任务选择页面
        taskRecyclerView = findViewById(R.id.task_recycler_view)
        directSearchInput = findViewById(R.id.direct_search_input)
        directSearchButton = findViewById(R.id.direct_search_button)

        // 步骤引导页面
        stepTitleText = findViewById(R.id.step_title_text)
        stepQuestionText = findViewById(R.id.step_question_text)
        stepInputLayout = findViewById(R.id.step_input_layout)
        stepInputText = findViewById(R.id.step_input_text)
        stepChoiceGroup = findViewById(R.id.step_choice_group)
        stepMultiChoiceLayout = findViewById(R.id.step_multi_choice_layout)
        prevStepButton = findViewById(R.id.prev_step_button)
        skipStepButton = findViewById(R.id.skip_step_button)
        nextStepButton = findViewById(R.id.next_step_button)

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

        // 加载当前设置
        loadSettings()

        // 设置下拉菜单适配器
        val displayModeAdapter = ArrayAdapter.createFromResource(
            this,
            R.array.display_mode_entries,
            android.R.layout.simple_spinner_item
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        displayModeSpinner.adapter = displayModeAdapter

        val themeAdapter = ArrayAdapter.createFromResource(
            this,
            R.array.theme_mode_entries,
            android.R.layout.simple_spinner_item
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        themeModeSpinner.adapter = themeAdapter

        val windowCountAdapter = ArrayAdapter.createFromResource(
            this,
            R.array.window_count_entries,
            android.R.layout.simple_spinner_item
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
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
     * 显示不支持语音输入的提示
     */
    private fun showNoVoiceSupportHint() {
        // 延迟显示提示，让UI先更新
        handler.postDelayed({
            Toast.makeText(this, "检测到设备不支持语音输入，已自动切换到文本输入模式", Toast.LENGTH_LONG).show()
        }, 500)
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
            val displayModeIndex = displayModeValues.indexOf(currentDisplayMode)
            if (displayModeIndex != -1) {
                displayModeSpinner.setSelection(displayModeIndex, false)
            }

            // 加载主题设置
            val themeMode = settingsManager.getThemeMode()
            Log.d(TAG, "Loading theme mode: $themeMode")

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

            Log.d(TAG, "Setting theme spinner to index: $themeIndex")
            themeModeSpinner.setSelection(themeIndex, false)

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
            val windowCount = settingsManager.getDefaultWindowCount()
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

    private fun showSettings() {
        currentState = UIState.SETTINGS
        chatLayout.visibility = View.GONE
        taskSelectionLayout.visibility = View.GONE
        stepGuidanceLayout.visibility = View.GONE
        promptPreviewLayout.visibility = View.GONE
        voiceLayout.visibility = View.GONE
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
        settingsLayout.visibility = View.GONE
        browserLayout.visibility = View.VISIBLE

        // 确保手势卡片式WebView管理器已正确初始化
        if (gestureCardWebViewManager == null) {
            setupBrowserWebView()
        }

        // 强制刷新UI状态，确保所有组件都处于正确状态
        forceRefreshUIState()

        updateTabColors()
    }

    private fun setupBottomNavigation() {
        // 对话tab (最左边)
        findViewById<LinearLayout>(R.id.tab_chat)?.setOnClickListener {
            showChat()
        }

        // 搜索tab (第二位)
        findViewById<LinearLayout>(R.id.tab_search)?.setOnClickListener {
            showBrowser()
        }

        // 任务tab (第三位，原首页)
        findViewById<LinearLayout>(R.id.tab_home)?.setOnClickListener {
            showTaskSelection()
        }

        // 语音tab (第四位)
        findViewById<LinearLayout>(R.id.tab_voice)?.setOnClickListener {
            showVoice()
        }

        // 设置tab (最右边)
        findViewById<LinearLayout>(R.id.tab_settings)?.setOnClickListener {
            showSettings()
        }

        // 初始化搜索tab图标
        updateSearchTabIcon()
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
     * 设置搜索引擎RecyclerView
     */
    private fun setupSearchEngineRecyclerView() {
        try {
            // 设置LayoutManager
            browserPreviewEngineList.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)

            // 初始化适配器
            draggableEngineAdapter = com.example.aifloatingball.adapter.DraggableSearchEngineAdapter(
                engines = mutableListOf(),
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
        browserLayout.visibility = View.GONE
        settingsLayout.visibility = View.GONE

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

            // 如果有WebView卡片且可以返回，则返回上一页
            val currentCard = gestureCardWebViewManager?.getCurrentCard()
            if (currentCard?.webView?.canGoBack() == true) {
                currentCard.webView.goBack()
                return
            }

            // 如果有WebView卡片但无法返回，显示主页
            if (gestureCardWebViewManager?.getAllCards()?.isNotEmpty() == true) {
                showBrowserHome()
                return
            }

            // 如果在主页，返回任务选择页面
            showTaskSelection()
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
     * 显示语音识别不可用对话框
     */
    private fun showVoiceRecognitionNotAvailableDialog() {
        AlertDialog.Builder(this)
            .setTitle("语音服务不可用")
            .setMessage("您的设备没有可用的语音识别服务。您可以：\n\n1. 安装或启用语音输入应用（如搜狗输入法、百度输入法等）\n2. 安装Google应用\n3. 检查系统设置中的语音输入选项")
            .setPositiveButton("手动输入") { dialog, _ ->
                // 允许用户手动输入文本
                voiceStatusText.text = "请在下方输入文本，然后点击搜索"
                voiceMicContainer.setCardBackgroundColor(ContextCompat.getColor(this, android.R.color.darker_gray))
                voiceTextInput.requestFocus()

                // 显示键盘
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(voiceTextInput, InputMethodManager.SHOW_IMPLICIT)

                // 确保搜索按钮可用
                voiceSearchButton.isEnabled = true
                Log.d(TAG, "切换到手动输入模式")
                dialog.dismiss()
            }
            .setNegativeButton("取消") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                // 启动悬浮球服务
                val serviceIntent = Intent(this, FloatingWindowService::class.java)
                startService(serviceIntent)

                // 启动HomeActivity
                val homeIntent = Intent(this, HomeActivity::class.java)
                homeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                startActivity(homeIntent)

                // 关闭当前Activity
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                // 启动灵动岛服务
                val serviceIntent = Intent(this, DynamicIslandService::class.java)
                startService(serviceIntent)

                // 启动HomeActivity
                val homeIntent = Intent(this, HomeActivity::class.java)
                homeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                startActivity(homeIntent)

                // 关闭当前Activity
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
            // 这样 HomeActivity 的 onResume 就不会再自动启动 SimpleModeActivity
            settingsManager.setDisplayMode("floating_ball")
            Log.d(TAG, "Changed display mode to floating_ball to prevent auto-restart")

            // 4. 启动悬浮球服务，确保用户仍然可以访问应用功能
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                Log.d(TAG, "Starting FloatingWindowService")
                val serviceIntent = Intent(this, FloatingWindowService::class.java)
                serviceIntent.putExtra("closing_from_simple_mode", true)
                startService(serviceIntent)

                // 启动HomeActivity但告诉它不要再启动SimpleModeActivity
                val homeIntent = Intent(this, HomeActivity::class.java)
                homeIntent.putExtra("closing_from_simple_mode", true)
                homeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(homeIntent)
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

    override fun onDestroy() {
        super.onDestroy()



        // 释放语音识别器
        releaseSpeechRecognizer()

        // 清理多页面WebView管理器
        multiPageWebViewManager?.destroy()
        multiPageWebViewManager = null

        // 清理四分之一圆弧操作栏
        quarterArcOperationBar?.let { operationBar ->
            (operationBar.parent as? ViewGroup)?.removeView(operationBar)
        }
        quarterArcOperationBar = null

        // 移除所有延迟任务
        handler.removeCallbacksAndMessages(null)
        longPressHandler.removeCallbacksAndMessages(null)
    }

    override fun onPause() {
        super.onPause()

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

                Log.d(TAG, "添加卡片: ${cardData.title}，ViewPager2已显示")
            }

            override fun onCardRemoved(cardData: GestureCardWebViewManager.WebViewCardData, position: Int) {
                // 如果没有卡片了，显示主页
                if (gestureCardWebViewManager?.getAllCards()?.isEmpty() == true) {
                    showBrowserHome()
                }

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

        // 设置手势检测
        setupBrowserGestureDetector()

        // 设置按钮监听器
        setupBrowserButtons()

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
                    // 智能返回逻辑
                    handleBrowserBackButtonClick(false)
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
            browserGestureDetector.onTouchEvent(event)
            false
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
        // 关闭按钮 - 智能返回逻辑（短按返回上一页，长按返回搜索tab首页）
        browserBtnClose.setOnClickListener {
            handleBrowserBackButtonClick(false) // 短按
        }

        // 设置长按监听器
        browserBtnClose.setOnLongClickListener {
            handleBrowserBackButtonClick(true) // 长按
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
            Log.d(TAG, "用户点击手势指南按钮")

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
                    showGestureHint()
                }, 100)
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

        // 创建快捷方式数据 - 修复移动端适配和错误网址
        val shortcuts = listOf(
            BrowserShortcut("百度", "https://m.baidu.com", R.drawable.ic_baidu),
            BrowserShortcut("谷歌", "https://www.google.com", R.drawable.ic_google),
            BrowserShortcut("必应", "https://cn.bing.com", R.drawable.ic_bing),
            BrowserShortcut("知乎", "https://www.zhihu.com", R.drawable.ic_zhihu),
            BrowserShortcut("微博", "https://m.weibo.cn", R.drawable.ic_weibo),
            BrowserShortcut("淘宝", "https://m.taobao.com", R.drawable.ic_taobao),
            BrowserShortcut("京东", "https://m.jd.com", R.drawable.ic_jd),
            BrowserShortcut("B站", "https://m.bilibili.com", R.drawable.ic_bilibili),
            BrowserShortcut("豆瓣", "https://m.douban.com/home_guide", R.drawable.ic_douban),
            BrowserShortcut("小红书", "https://www.xiaohongshu.com", R.drawable.ic_xiaohongshu),
            BrowserShortcut("腾讯新闻", "https://xw.qq.com", R.drawable.ic_toutiao),
            BrowserShortcut("新浪新闻", "https://news.sina.cn", R.drawable.ic_weibo),
            BrowserShortcut("网易新闻", "https://3g.163.com/news", R.drawable.ic_web_default),
            BrowserShortcut("搜狐新闻", "https://m.sohu.com", R.drawable.ic_web_default),
            BrowserShortcut("人民网", "https://wap.people.com.cn", R.drawable.ic_web_default),
            BrowserShortcut("新华网", "https://m.xinhuanet.com", R.drawable.ic_web_default),
            BrowserShortcut("知网", "https://i.cnki.net/newHome.html", R.drawable.ic_web_default),
            BrowserShortcut("万方", "https://m.wanfangdata.com.cn", R.drawable.ic_web_default),
            BrowserShortcut("维普", "https://m.cqvip.com", R.drawable.ic_web_default),
            BrowserShortcut("美团", "https://i.meituan.com", R.drawable.ic_web_default),
            BrowserShortcut("CSDN", "https://m.csdn.net", R.drawable.ic_web_default),
            BrowserShortcut("稀土掘金", "https://juejin.cn", R.drawable.ic_web_default)
        )

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
        )
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
                    cardPreviewOverlay.hide()
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
            Log.d(TAG, "开始创建新卡片")

            // 先隐藏所有覆盖层
            hideAllOverlays()

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
            }

            if (newCard != null) {
                Log.d(TAG, "新卡片创建成功: ${newCard.id}")

                // 检查卡片总数
                val mobileCardCount = mobileCardManager?.getAllCards()?.size ?: 0
                val gestureCardCount = gestureCardWebViewManager?.getAllCards()?.size ?: 0
                Log.d(TAG, "当前卡片数量 - 手机卡片: $mobileCardCount, 手势卡片: $gestureCardCount")

                // 显示成功提示
                Toast.makeText(this, "新卡片已创建 (总计: ${mobileCardCount + gestureCardCount})", Toast.LENGTH_SHORT).show()

                // 确保UI状态正确切换
                browserLayout.post {
                    forceRefreshUIState()
                }
            } else {
                Log.e(TAG, "新卡片创建失败")
                Toast.makeText(this, "创建卡片失败", Toast.LENGTH_SHORT).show()
            }

        } catch (e: Exception) {
            Log.e(TAG, "创建新卡片时发生错误", e)
            Toast.makeText(this, "创建卡片时发生错误: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 设置新的手机卡片管理器
     */
    private fun setupMobileCardManager() {
        Log.d(TAG, "开始设置手机卡片管理器")

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
                Log.d(TAG, "所有手机卡片已移除")
            }
        })

        Log.d(TAG, "手机卡片管理器设置完成")
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
                    showContactOptionsDialog(contact)
                    true
                }
            )

            // 设置RecyclerView
            chatContactsRecyclerView?.apply {
                layoutManager = LinearLayoutManager(this@SimpleModeActivity)
                adapter = chatContactAdapter
            }

            // 保存适配器引用
            this.chatContactAdapter = chatContactAdapter

            // 设置搜索功能
            chatSearchInput?.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    // 实现搜索逻辑
                    performChatSearch(s?.toString() ?: "")
                }
            })

            // 设置添加联系人按钮
            chatAddContactButton?.setOnClickListener {
                // 打开添加联系人界面
                openAddContactDialog()
            }

            // 设置TabLayout
            chatTabLayout?.apply {
                // 添加标签页
                addTab(newTab().setText("全部"))
                addTab(newTab().setText("AI助手"))

                addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
                    override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                        when (tab?.position) {
                            0 -> chatContactAdapter?.updateContacts(allContacts)
                            1 -> showAIContacts()
                        }
                    }

                    override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
                    override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
                })
            }

            // 加载初始数据
            loadInitialContacts()

            Log.d(TAG, "对话功能初始化完成")
        } catch (e: Exception) {
            Log.e(TAG, "设置对话功能失败", e)
        }
    }

    /**
     * 执行对话搜索
     */
    private fun performChatSearch(query: String) {
        try {
            chatContactAdapter?.searchContacts(query)
            Log.d(TAG, "执行对话搜索: $query")
        } catch (e: Exception) {
            Log.e(TAG, "搜索失败", e)
        }
    }

    /**
     * 显示AI联系人
     */
    private fun showAIContacts() {
        try {
            val aiContacts = allContacts.filter { it.name == "AI助手" }
            chatContactAdapter?.updateContacts(aiContacts)
            Log.d(TAG, "显示AI联系人")
        } catch (e: Exception) {
            Log.e(TAG, "显示AI联系人失败", e)
        }
    }



    /**
     * 打开添加联系人对话框
     */
    private fun openAddContactDialog() {
        try {
            val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_contact, null)
            val dialog = AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(true)
                .create()

            // 设置AI助手选项点击事件
            dialogView.findViewById<MaterialCardView>(R.id.ai_contact_option)?.setOnClickListener {
                dialog.dismiss()
                openAddAIContactDialog()
            }

            // 设置填写API选项点击事件
            dialogView.findViewById<MaterialCardView>(R.id.api_contact_option)?.setOnClickListener {
                dialog.dismiss()
                Log.d(TAG, "用户点击了填写API选项")
                openAddAPIContactDialog()
            }



            // 设置取消按钮点击事件
            dialogView.findViewById<MaterialButton>(R.id.cancel_button)?.setOnClickListener {
                dialog.dismiss()
            }

            dialog.show()
            Log.d(TAG, "打开添加联系人对话框")
        } catch (e: Exception) {
            Log.e(TAG, "打开添加联系人对话框失败", e)
            Toast.makeText(this, "打开添加联系人对话框失败", Toast.LENGTH_SHORT).show()
        }
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
                    val contact = ChatContact(
                        id = "ai_${aiName.lowercase().replace(" ", "_")}",
                        name = aiName,
                        type = ContactType.AI,
                        description = description,
                        isOnline = true,
                        lastMessage = "你好！我是$aiName，有什么可以帮助你的吗？",
                        lastMessageTime = System.currentTimeMillis() - (Math.random() * 86400000).toLong(), // 随机时间
                        unreadCount = 0,
                        isPinned = true, // 有API配置的AI优先显示
                        customData = mapOf(
                            "api_url" to getDefaultApiUrl(aiName),
                            "api_key" to apiKey,
                            "model" to getDefaultModel(aiName)
                        )
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

            // 创建分类
            allContacts = mutableListOf(
                ContactCategory("AI助手", aiContacts)
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
     * 显示联系人选项对话框
     */
    private fun showContactOptionsDialog(contact: ChatContact) {
        try {
            val options = mutableListOf<String>()

            // 根据当前状态动态添加选项
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

            AlertDialog.Builder(this)
                .setTitle("${contact.name} 选项")
                .setItems(options.toTypedArray()) { _, which ->
                    when (options[which]) {
                        "置顶" -> toggleContactPin(contact, true)
                        "取消置顶" -> toggleContactPin(contact, false)
                        "静音" -> toggleContactMute(contact, true)
                        "取消静音" -> toggleContactMute(contact, false)
                        "删除" -> deleteContact(contact)
                    }
                }
                .setNegativeButton("取消", null)
                .show()
        } catch (e: Exception) {
            Log.e(TAG, "显示联系人选项对话框失败", e)
        }
    }

    /**
     * 切换联系人置顶状态
     */
    private fun toggleContactPin(contact: ChatContact, isPinned: Boolean) {
        try {
            // 找到联系人所在的分类和位置
            val categoryIndex = allContacts.indexOfFirst { category ->
                category.contacts.any { it.id == contact.id }
            }

            if (categoryIndex != -1) {
                val category = allContacts[categoryIndex]
                val contactIndex = category.contacts.indexOfFirst { it.id == contact.id }

                if (contactIndex != -1) {
                    val updatedContacts = category.contacts.toMutableList()
                    val updatedContact = contact.copy(isPinned = isPinned)
                    updatedContacts[contactIndex] = updatedContact

                    // 重新排序：置顶的联系人在前面
                    val sortedContacts = if (isPinned) {
                        updatedContacts.sortedWith(compareByDescending<ChatContact> { it.isPinned }
                            .thenByDescending { it.lastMessageTime })
                    } else {
                        updatedContacts.sortedWith(compareByDescending<ChatContact> { it.isPinned }
                            .thenByDescending { it.lastMessageTime })
                    }

                    allContacts[categoryIndex] = category.copy(contacts = sortedContacts)

                    // 保存更新后的联系人数据
                    saveContacts()

                    chatContactAdapter?.updateContacts(allContacts)

                    val action = if (isPinned) "已置顶" else "已取消置顶"
                    Toast.makeText(this, "${contact.name} $action", Toast.LENGTH_SHORT).show()
                    Log.d(TAG, "切换联系人置顶状态: ${contact.name} -> $isPinned")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "切换联系人置顶状态失败", e)
        }
    }

    /**
     * 切换联系人静音状态
     */
    private fun toggleContactMute(contact: ChatContact, isMuted: Boolean) {
        try {
            // 找到联系人所在的分类和位置
            val categoryIndex = allContacts.indexOfFirst { category ->
                category.contacts.any { it.id == contact.id }
            }

            if (categoryIndex != -1) {
                val category = allContacts[categoryIndex]
                val contactIndex = category.contacts.indexOfFirst { it.id == contact.id }

                if (contactIndex != -1) {
                    val updatedContacts = category.contacts.toMutableList()
                    val updatedContact = contact.copy(isMuted = isMuted)
                    updatedContacts[contactIndex] = updatedContact

                    allContacts[categoryIndex] = category.copy(contacts = updatedContacts)

                    // 保存更新后的联系人数据
                    saveContacts()

                    chatContactAdapter?.updateContacts(allContacts)

                    val action = if (isMuted) "已静音" else "已取消静音"
                    Toast.makeText(this, "${contact.name} $action", Toast.LENGTH_SHORT).show()
                    Log.d(TAG, "切换联系人静音状态: ${contact.name} -> $isMuted")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "切换联系人静音状态失败", e)
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
                "文心一言" -> "wenxin_api_key"
                "通义千问" -> "qianwen_api_key"
                "讯飞星火" -> "xinghuo_api_key"
                "kimi" -> "kimi_api_key"
                else -> "${aiName.lowercase()}_api_key"
            }
            settingsManager.putString(keyName, apiKey)
            Log.d(TAG, "保存API密钥: $keyName")
        } catch (e: Exception) {
            Log.e(TAG, "保存API密钥失败", e)
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
                )
            )

            // 添加到联系人列表
            addContactToList(newContact)
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
            "kimi" -> "https://kimi.moonshot.cn/api/chat-messages"
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
                "文心一言" to "百度的大语言模型",
                "通义千问" to "阿里巴巴的大语言模型",
                "讯飞星火" to "科大讯飞的AI助手",
                "Kimi" to "Moonshot的AI助手"
            )

            val modelNames = aiModels.map { it.first }.toTypedArray()

            // 设置Spinner适配器
            val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, modelNames)
            spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
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
                            )
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
     * 删除联系人
     */
    private fun deleteContact(contact: ChatContact) {
        try {
            AlertDialog.Builder(this)
                .setTitle("删除联系人")
                .setMessage("确定要删除 ${contact.name} 吗？")
                .setPositiveButton("删除") { _, _ ->
                    // 从联系人列表中删除
                    removeContactFromList(contact)
                    Toast.makeText(this, "${contact.name} 已删除", Toast.LENGTH_SHORT).show()
                    Log.d(TAG, "删除联系人: ${contact.name}")
                }
                .setNegativeButton("取消", null)
                .show()
        } catch (e: Exception) {
            Log.e(TAG, "删除联系人失败", e)
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
                    // 如果分类中没有联系人了，删除整个分类
                    allContacts.removeAt(categoryIndex)
                } else {
                    // 更新分类中的联系人列表
                    allContacts[categoryIndex] = category.copy(contacts = updatedContacts)
                }

                // 保存更新后的联系人数据
                saveContacts()

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
        // 目前没有需要处理的Activity结果
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
                )

                allContacts[categoryIndex] = category.copy(contacts = sortedContacts)
            } else {
                // 分类不存在，创建新分类
                val newCategory = ContactCategory(categoryName, listOf(contact))
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
}