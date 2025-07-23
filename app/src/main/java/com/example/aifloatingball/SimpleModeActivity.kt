package com.example.aifloatingball

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.drawerlayout.widget.DrawerLayout
import androidx.viewpager2.widget.ViewPager2
import androidx.core.view.GravityCompat
import androidx.core.view.GestureDetectorCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.button.MaterialButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.aifloatingball.adapter.TaskTemplateAdapter
import com.example.aifloatingball.data.SimpleTaskTemplates
import com.example.aifloatingball.model.PromptTemplate
import com.example.aifloatingball.model.PromptField
import com.example.aifloatingball.model.FieldType
import com.example.aifloatingball.model.UserPromptData

import com.example.aifloatingball.service.SimpleModeService
import com.example.aifloatingball.service.FloatingWindowService
import com.example.aifloatingball.service.DynamicIslandService
import com.example.aifloatingball.voice.VoicePromptBranchManager
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import androidx.appcompat.app.AppCompatDelegate
import android.provider.Settings
import android.os.Build

class SimpleModeActivity : AppCompatActivity(), VoicePromptBranchManager.BranchViewListener {
    
    companion object {
        private const val TAG = "SimpleModeActivity"
        // 为交互模式定义一个存储键
        private const val KEY_VOICE_INTERACTION_MODE = "voice_interaction_mode"
    }
    
    // 界面状态
    private enum class UIState {
        TASK_SELECTION,    // 任务选择页面
        STEP_GUIDANCE,     // 步骤引导页面
        PROMPT_PREVIEW,    // Prompt预览页面
        VOICE,             // 语音页面
        BROWSER,           // 浏览器页面
        SETTINGS           // 设置页面
    }
    
    private var currentState = UIState.TASK_SELECTION
    private var currentTemplate: PromptTemplate? = null
    private var currentStepIndex = 0
    private lateinit var userPromptData: UserPromptData
    private lateinit var settingsManager: SettingsManager

    // UI组件
    private lateinit var taskSelectionLayout: LinearLayout
    private lateinit var stepGuidanceLayout: LinearLayout
    private lateinit var promptPreviewLayout: LinearLayout
    private lateinit var voiceLayout: LinearLayout
    private lateinit var browserLayout: androidx.drawerlayout.widget.DrawerLayout
    private lateinit var settingsLayout: ScrollView
    
    // 任务选择页面组件
    private lateinit var taskRecyclerView: RecyclerView
    private lateinit var taskAdapter: TaskTemplateAdapter
    private lateinit var directSearchInput: EditText
    private lateinit var directSearchButton: ImageButton
    
    // 步骤引导页面组件
    private lateinit var stepTitleText: TextView
    private lateinit var stepQuestionText: TextView
    private lateinit var stepInputLayout: TextInputLayout
    private lateinit var stepInputText: TextInputEditText
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
    private lateinit var voiceTextInputLayout: TextInputLayout
    private lateinit var voiceTextInput: TextInputEditText
    private lateinit var voiceClearButton: MaterialButton
    private lateinit var voiceSearchButton: MaterialButton
    private lateinit var voiceInteractionToggleButton: ImageButton

    // 浏览器页面组件 - 完整功能版本
    private lateinit var browserWebViewContainer: FrameLayout
    private lateinit var browserWebView: WebView
    private lateinit var browserHomeContent: LinearLayout
    private lateinit var browserBtnClose: ImageButton
    private lateinit var browserSearchInput: EditText
    private lateinit var browserBtnMenu: ImageButton
    private lateinit var browserProgressBar: ProgressBar
    private lateinit var browserShortcutsGrid: androidx.recyclerview.widget.RecyclerView
    private lateinit var browserGestureHint: TextView
    private lateinit var browserNavDrawer: LinearLayout
    private lateinit var browserLetterTitle: TextView
    private lateinit var browserPreviewEngineList: LinearLayout
    private lateinit var browserExitButton: Button
    private lateinit var browserLetterIndexBar: com.example.aifloatingball.view.LetterIndexBar

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
    private lateinit var notificationListenerSwitch: SwitchMaterial
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
        
        initializeViews()
        setupTaskSelection()
        showTaskSelection()
        
        // 处理从其他地方传入的搜索内容
        handleIntentData()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        // 配置变化时重新应用颜色
        updateUIColors()
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
        when (themeMode) {
            SettingsManager.THEME_MODE_LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            SettingsManager.THEME_MODE_DARK -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }
    
    /**
     * 动态更新界面颜色
     */
    private fun updateUIColors() {
        val isDarkMode = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                         android.content.res.Configuration.UI_MODE_NIGHT_YES

        // 更新状态栏和导航栏颜色
        window.statusBarColor = androidx.core.content.ContextCompat.getColor(this, R.color.simple_mode_status_bar_light)
        window.navigationBarColor = androidx.core.content.ContextCompat.getColor(this, R.color.simple_mode_navigation_bar_light)

        // 更新根布局背景
        findViewById<View>(android.R.id.content).setBackgroundColor(
            androidx.core.content.ContextCompat.getColor(this, R.color.simple_mode_background_light))

        // 更新主布局背景 - 修复ClassCastException
        val mainLayout = findViewById<LinearLayout>(R.id.simple_mode_main_layout)
        mainLayout?.setBackgroundColor(
            androidx.core.content.ContextCompat.getColor(this, R.color.simple_mode_background_light))

        // 更新标题栏颜色
        updateHeaderColors()

        // 更新底部导航颜色
        updateBottomNavigationColors()

        // 更新所有文本颜色
        updateAllTextColors()
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
        for (i in 0 until viewGroup.childCount) {
            val child = viewGroup.getChildAt(i)
            when (child) {
                is TextView -> {
                    // 根据TextView的用途设置不同的颜色
                    when (child.id) {
                        R.id.simple_mode_title -> {
                            child.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.simple_mode_header_text_light))
                        }
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
     * 更新标题栏颜色
     */
    private fun updateHeaderColors() {
        val headerLayout = findViewById<LinearLayout>(R.id.simple_mode_header) ?: return
        val titleText = headerLayout.findViewById<TextView>(R.id.simple_mode_title) ?: return
        val minimizeButton = headerLayout.findViewById<ImageButton>(R.id.simple_mode_minimize_button) ?: return
        val closeButton = headerLayout.findViewById<ImageButton>(R.id.simple_mode_close_button) ?: return
        
        // 设置标题栏背景色
        headerLayout.setBackgroundColor(androidx.core.content.ContextCompat.getColor(this, R.color.simple_mode_header_background_light))
        
        // 设置标题文字颜色
        titleText.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.simple_mode_header_text_light))
        
        // 设置图标颜色
        val iconColor = androidx.core.content.ContextCompat.getColor(this, R.color.simple_mode_header_icon_light)
        minimizeButton.setColorFilter(iconColor)
        closeButton.setColorFilter(iconColor)
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
            
            val isSelected = when (i) {
                0 -> currentState == UIState.TASK_SELECTION
                1 -> currentState == UIState.BROWSER // 搜索tab
                2 -> currentState == UIState.VOICE
                3 -> currentState == UIState.SETTINGS
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
        taskSelectionLayout = findViewById(R.id.task_selection_layout)
        stepGuidanceLayout = findViewById(R.id.step_guidance_layout)
        promptPreviewLayout = findViewById(R.id.prompt_preview_layout)
        voiceLayout = findViewById(R.id.voice_layout)
        browserLayout = findViewById(R.id.browser_layout)
        settingsLayout = findViewById(R.id.settings_layout)
        
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
        voiceInteractionToggleButton = findViewById(R.id.voice_interaction_mode_toggle)

        // 浏览器页面 - 完整功能版本组件初始化
        browserWebViewContainer = findViewById(R.id.browser_webview_container)
        browserWebView = findViewById(R.id.browser_webview)
        browserHomeContent = findViewById(R.id.browser_home_content)
        browserBtnClose = findViewById(R.id.browser_btn_close)
        browserSearchInput = findViewById(R.id.browser_search_input)
        browserBtnMenu = findViewById(R.id.browser_btn_menu)
        browserProgressBar = findViewById(R.id.browser_progress_bar)
        browserShortcutsGrid = findViewById(R.id.browser_shortcuts_grid)
        browserGestureHint = findViewById(R.id.browser_gesture_hint)
        browserNavDrawer = findViewById(R.id.browser_nav_drawer)
        browserLetterTitle = findViewById(R.id.browser_letter_title)
        browserPreviewEngineList = findViewById(R.id.browser_preview_engine_list)
        browserExitButton = findViewById(R.id.browser_exit_button)
        browserLetterIndexBar = findViewById(R.id.browser_letter_index_bar)

        // 创建虚拟组件用于兼容性（这些组件在当前布局中不存在）
        browserViewPager = androidx.viewpager2.widget.ViewPager2(this)
        browserTabPreviewContainer = LinearLayout(this)
        browserBtnAddTab = ImageButton(this)
        browserAppbar = com.google.android.material.appbar.AppBarLayout(this)
        browserVoiceSearch = ImageButton(this)
        browserBottomBar = LinearLayout(this)
        browserLeftButtons = LinearLayout(this)
        browserRightButtons = LinearLayout(this)
        browserBtnHistory = ImageButton(this)
        browserBtnBookmarks = ImageButton(this)
        browserBtnSettings = ImageButton(this)
        browserAutoHideSwitch = androidx.appcompat.widget.SwitchCompat(this)
        browserClipboardSwitch = androidx.appcompat.widget.SwitchCompat(this)
        
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
        
        // 设置搜索框监听器
        setupSearchListeners()
        
        // 设置语音页面
        setupVoicePage()
        
        // 设置设置页面
        setupSettingsPage()
        
        // 设置顶部按钮
        val minimizeButton = findViewById<ImageButton>(R.id.simple_mode_minimize_button)
        Log.d(TAG, "Minimize button found: ${minimizeButton != null}")
        if (minimizeButton == null) {
            Toast.makeText(this, "警告：找不到最小化按钮", Toast.LENGTH_LONG).show()
        }
        
        minimizeButton?.setOnClickListener {
            Log.d(TAG, "Minimize button clicked!")
            Toast.makeText(this, "最小化按钮被点击", Toast.LENGTH_SHORT).show()
            
            // 添加一个简单的测试
            Log.d(TAG, "About to call minimizeToService")
            Toast.makeText(this, "即将调用minimizeToService", Toast.LENGTH_SHORT).show()
            
            try {
            minimizeToService()
            } catch (e: Exception) {
                Log.e(TAG, "Exception in minimize button click", e)
                Toast.makeText(this, "按钮点击异常: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
        
        // 检查关闭按钮是否存在
        val closeButton = findViewById<ImageButton>(R.id.simple_mode_close_button)
        Log.d(TAG, "Close button found: ${closeButton != null}")
        
        closeButton?.setOnClickListener {
            Log.d(TAG, "Close button clicked!")
            Toast.makeText(this, "关闭按钮被点击", Toast.LENGTH_SHORT).show()
            
            // 记录当前显示模式和服务状态
            val currentMode = settingsManager.getDisplayMode()
            val serviceRunning = SimpleModeService.isRunning(this)
            Log.d(TAG, "Before closing - Display mode: $currentMode, SimpleModeService running: $serviceRunning")
            
            closeSimpleMode()
        }
        
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
        voiceInteractionToggleButton.setOnClickListener {
            promptBranchManager.interactionMode = if (promptBranchManager.interactionMode == VoicePromptBranchManager.InteractionMode.CLICK) {
                it.contentDescription = "切换到点击模式"
                Toast.makeText(this, "已切换到拖动模式", Toast.LENGTH_SHORT).show()
                (it as ImageButton).setImageResource(R.drawable.ic_drag_handle) // 更新图标
                // 保存设置
                settingsManager.putString(KEY_VOICE_INTERACTION_MODE, "DRAG")
                VoicePromptBranchManager.InteractionMode.DRAG
            } else {
                it.contentDescription = "切换到拖动模式"
                Toast.makeText(this, "已切换到点击模式", Toast.LENGTH_SHORT).show()
                (it as ImageButton).setImageResource(R.drawable.ic_click) // 更新图标
                 // 保存设置
                settingsManager.putString(KEY_VOICE_INTERACTION_MODE, "CLICK")
                VoicePromptBranchManager.InteractionMode.CLICK
            }
        }
        // 根据保存的模式初始化按钮图标
        if (promptBranchManager.interactionMode == VoicePromptBranchManager.InteractionMode.DRAG) {
            voiceInteractionToggleButton.setImageResource(R.drawable.ic_drag_handle)
        } else {
            voiceInteractionToggleButton.setImageResource(R.drawable.ic_click)
        }

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
                    settingsManager.setDisplayMode(selectedMode)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        themeModeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedTheme = when(position) {
                    0 -> SettingsManager.THEME_MODE_LIGHT
                    1 -> SettingsManager.THEME_MODE_DARK
                    else -> SettingsManager.THEME_MODE_SYSTEM
                }
                settingsManager.setThemeMode(selectedTheme)
                applyTheme()
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
            try {
                startActivity(Intent(this, SearchEngineManagerActivity::class.java))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open SearchEngineSettings", e)
                Toast.makeText(this, "无法打开搜索引擎设置", Toast.LENGTH_SHORT).show()
            }
        }

        aiSearchEngineSettingsItem.setOnClickListener {
            try {
                startActivity(Intent(this, AISearchEngineSettingsActivity::class.java))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open AISearchEngineSettings", e)
                Toast.makeText(this, "无法打开AI搜索引擎设置", Toast.LENGTH_SHORT).show()
            }
        }

        masterPromptSettingsItem.setOnClickListener {
            try {
                startActivity(Intent(this, MasterPromptSettingsActivity::class.java))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open MasterPromptSettings", e)
                Toast.makeText(this, "无法打开Master Prompt设置", Toast.LENGTH_SHORT).show()
            }
        }

        appSearchSettingsItem.setOnClickListener {
            try {
                startActivity(Intent(this, com.example.aifloatingball.settings.AppSearchSettingsActivity::class.java))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open AppSearchSettings", e)
                Toast.makeText(this, "无法打开应用搜索设置", Toast.LENGTH_SHORT).show()
            }
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
    }

    /**
     * 更新左右手模式布局
     */
    private fun updateLayoutForHandedness(isLeftHanded: Boolean) {
        // 更新UI布局以适应左右手模式
        val rootLayout = findViewById<ViewGroup>(android.R.id.content)
        if (rootLayout != null) {
            // 镜像翻转整个布局
            rootLayout.scaleX = if (isLeftHanded) -1f else 1f
            
            // 修正文本方向
            val textViews = ArrayList<TextView>()
            findAllTextViews(rootLayout, textViews)
            for (textView in textViews) {
                textView.scaleX = if (isLeftHanded) -1f else 1f
            }
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
            themeModeSpinner.setSelection(when(themeMode) {
                SettingsManager.THEME_MODE_LIGHT -> 0
                SettingsManager.THEME_MODE_DARK -> 1
                else -> 2
            }, false)

            // 加载开关状态
            clipboardMonitorSwitch.isChecked = settingsManager.isClipboardListenerEnabled()
            autoHideSwitch.isChecked = settingsManager.getAutoHide()
            aiModeSwitch.isChecked = settingsManager.getIsAIMode()
            leftHandedSwitch.isChecked = settingsManager.isLeftHandedModeEnabled()
            autoPasteSwitch.isChecked = settingsManager.isAutoPasteEnabled()
            notificationListenerSwitch.isChecked = settingsManager.getBoolean("enable_notification_listener", false)

            // 加载透明度设置 - getBallAlpha()返回0-255，需要转换为0-100
            ballAlphaSeekbar.progress = ((settingsManager.getBallAlpha() / 255.0) * 100).toInt()

            // 加载窗口数量设置
            val windowCount = settingsManager.getDefaultWindowCount()
            windowCountSpinner.setSelection(windowCount - 1) // 转换为0-based索引

            // 立即应用左手模式
            updateLayoutForHandedness(settingsManager.isLeftHandedModeEnabled())
        } catch (e: Exception) {
            Log.e(TAG, "Error loading settings", e)
            Toast.makeText(this, "加载设置时出错", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showVoice() {
        currentState = UIState.VOICE
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
        taskSelectionLayout.visibility = View.GONE
        stepGuidanceLayout.visibility = View.GONE
        promptPreviewLayout.visibility = View.GONE
        voiceLayout.visibility = View.GONE
        browserLayout.visibility = View.GONE
        settingsLayout.visibility = View.VISIBLE

        loadSettings() // 每次显示时重新加载设置
        updateTabColors()
    }

    private fun showBrowser() {
        currentState = UIState.BROWSER
        taskSelectionLayout.visibility = View.GONE
        stepGuidanceLayout.visibility = View.GONE
        promptPreviewLayout.visibility = View.GONE
        voiceLayout.visibility = View.GONE
        settingsLayout.visibility = View.GONE
        browserLayout.visibility = View.VISIBLE

        // 确保浏览器已正确初始化
        if (!::browserWebView.isInitialized) {
            setupBrowserWebView()
        }

        // 显示主页内容
        showBrowserHome()

        updateTabColors()
    }
    
    private fun setupBottomNavigation() {
        findViewById<LinearLayout>(R.id.tab_home)?.setOnClickListener {
            showTaskSelection()
        }
        
        findViewById<LinearLayout>(R.id.tab_search)?.setOnClickListener {
            showBrowser()
        }
        
        findViewById<LinearLayout>(R.id.tab_voice)?.setOnClickListener {
            showVoice()
        }
        
        findViewById<LinearLayout>(R.id.tab_settings)?.setOnClickListener {
            showSettings()
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
    
    private fun showTaskSelection() {
        currentState = UIState.TASK_SELECTION
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
        
        if (currentStepIndex >= fields.size) {
            // 所有步骤完成，显示预览
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
        prevStepButton.isEnabled = currentStepIndex > 0
        skipStepButton.visibility = if (currentField.isOptional) View.VISIBLE else View.GONE
        
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
        nextStepButton.setOnClickListener {
            if (collectCurrentStepData(field)) {
                currentStepIndex++
                setupCurrentStep()
            }
        }
        
        prevStepButton.setOnClickListener {
            currentStepIndex--
            setupCurrentStep()
        }
        
        skipStepButton.setOnClickListener {
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

        browserWebView.loadUrl(searchUrl)
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
            // 如果抽屉打开，先关闭抽屉
            if (browserLayout.isDrawerOpen(GravityCompat.START)) {
                browserLayout.closeDrawer(GravityCompat.START)
                return
            }

            // 如果WebView可见且可以返回，则返回上一页
            if (browserWebView.visibility == View.VISIBLE && browserWebView.canGoBack()) {
                browserWebView.goBack()
                return
            }

            // 如果WebView可见但无法返回，显示主页
            if (browserWebView.visibility == View.VISIBLE) {
                showBrowserHome()
                return
            }

            // 如果在主页，返回任务选择页面
            showTaskSelection()
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
        val textInputLayout = findViewById<TextInputLayout>(R.id.voice_text_input_layout)
        
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
        voiceStatusText.text = "点击麦克风开始语音输入"
        voiceTextInput.setText("")
        recognizedText = ""
        isListening = false
        voiceMicContainer.setCardBackgroundColor(ContextCompat.getColor(this, R.color.colorPrimary))
        voiceSearchButton.isEnabled = false
    }
    
    private fun toggleVoiceRecognition() {
        if (isListening) {
            stopVoiceRecognition()
        } else {
            startVoiceRecognition()
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
        
        // 检查语音识别可用性
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            voiceStatusText.text = "设备不支持语音识别"
            return
        }
        
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
            Log.e(TAG, "语音识别启动失败", e)
            voiceStatusText.text = "语音识别启动失败"
            isListening = false
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
            else -> "识别错误"
        }
        
        // 对于非致命错误，自动重试
        if (error == SpeechRecognizer.ERROR_NO_MATCH || 
            error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
            voiceStatusText.text = "请再说一遍"
            handler.postDelayed({
                if (isListening) {
                    startVoiceRecognition()
                }
            }, 500)
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
                .withEndAction {
                // 启动最小化服务
                val serviceIntent = Intent(this, SimpleModeService::class.java).apply {
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
                .start()
    }

    private fun closeSimpleMode() {
        Log.d(TAG, "closeSimpleMode() called")
        try {
            // 1. 禁用按钮防止重复点击
            findViewById<ImageButton>(R.id.simple_mode_close_button)?.isEnabled = false
            
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
            // 恢复按钮状态
            findViewById<ImageButton>(R.id.simple_mode_close_button)?.isEnabled = true
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

        // 清理WebView资源
        if (::browserWebView.isInitialized) {
            browserWebView.clearHistory()
            browserWebView.clearCache(true)
            browserWebView.loadUrl("about:blank")
            browserWebView.onPause()
            browserWebView.removeAllViews()
            browserWebView.destroyDrawingCache()
            browserWebView.destroy()
        }

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
     * 设置浏览器WebView - 参考HomeActivity的完整实现
     */
    private fun setupBrowserWebView() {
        // 配置WebView设置 - 完全参考HomeActivity
        browserWebView.settings.apply {
            // 设置移动端User-Agent
            userAgentString = "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

            // 基本设置
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true

            // 缓存设置
            cacheMode = WebSettings.LOAD_DEFAULT

            // 页面自适应
            useWideViewPort = true
            loadWithOverviewMode = true

            // 缩放设置
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false

            // 多窗口支持
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = true

            // 混合内容
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

            // 设置默认编码
            defaultTextEncodingName = "UTF-8"

            // 允许文件访问
            allowFileAccess = true
            allowContentAccess = true
        }

        // 设置WebViewClient - 参考HomeActivity + 广告拦截
        browserWebView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                // 拦截广告和恶意URL
                if (url != null && isAdOrMaliciousUrl(url)) {
                    Log.d(TAG, "拦截广告URL: $url")
                    return true // 拦截该URL
                }
                return false // 让WebView处理URL加载
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                browserProgressBar.visibility = View.VISIBLE
                browserProgressBar.progress = 0
                Log.d(TAG, "页面开始加载: $url")
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                browserProgressBar.visibility = View.GONE

                // 注入广告拦截JavaScript
                injectAdBlockingScript(view)

                // 只在特定情况下更新搜索框URL
                url?.let { currentUrl ->
                    val currentInput = browserSearchInput.text.toString().trim()

                    // 如果当前输入框为空，或者输入的是之前的URL，则更新为新URL
                    // 但不要覆盖用户正在输入的搜索关键词
                    if (currentInput.isEmpty() ||
                        currentInput.startsWith("http") ||
                        currentInput == currentUrl ||
                        android.webkit.URLUtil.isValidUrl(currentInput)) {
                        browserSearchInput.setText(currentUrl)
                    }

                    Log.d(TAG, "页面加载完成: $currentUrl")
                }
            }

            override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                super.onReceivedError(view, errorCode, description, failingUrl)
                Log.e(TAG, "WebView加载错误: $description")
                Toast.makeText(this@SimpleModeActivity, "页面加载失败: $description", Toast.LENGTH_SHORT).show()
            }
        }

        // 设置WebChromeClient处理进度条
        browserWebView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress < 100) {
                    browserProgressBar.visibility = View.VISIBLE
                    browserProgressBar.progress = newProgress
                } else {
                    browserProgressBar.visibility = View.GONE
                }
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)
                Log.d(TAG, "页面标题: $title")
            }
        }

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

        // 初始显示主页内容
        showBrowserHome()
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
     * 设置浏览器按钮监听器
     */
    private fun setupBrowserButtons() {
        // 关闭按钮 - 智能返回逻辑
        browserBtnClose.setOnClickListener {
            if (browserWebView.visibility == View.VISIBLE && browserWebView.canGoBack()) {
                browserWebView.goBack()
            } else if (browserWebView.visibility == View.VISIBLE) {
                showBrowserHome()
            } else {
                showTaskSelection()
            }
        }

        // 菜单按钮 - 打开搜索引擎侧边栏
        browserBtnMenu.setOnClickListener {
            if (browserLayout.isDrawerOpen(GravityCompat.START)) {
                browserLayout.closeDrawer(GravityCompat.START)
            } else {
                browserLayout.openDrawer(GravityCompat.START)
            }
        }
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
            }

            override fun onDrawerOpened(drawerView: View) {
                drawerView.alpha = 1.0f
                // 打开抽屉时更新搜索引擎列表
                updateBrowserEngineList('#')
            }

            override fun onDrawerClosed(drawerView: View) {
                // 抽屉关闭
            }

            override fun onDrawerStateChanged(newState: Int) {}
        })

        // 设置退出按钮点击监听器
        browserExitButton.setOnClickListener {
            browserLayout.closeDrawer(GravityCompat.START)
        }

        // 设置字母索引栏
        setupBrowserLetterIndexBar()
    }

    /**
     * 设置浏览器快捷方式
     */
    private fun setupBrowserShortcuts() {
        browserShortcutsGrid.layoutManager = GridLayoutManager(this, 4)

        // 创建快捷方式数据
        val shortcuts = listOf(
            BrowserShortcut("百度", "https://www.baidu.com", R.drawable.ic_baidu),
            BrowserShortcut("谷歌", "https://www.google.com", R.drawable.ic_google),
            BrowserShortcut("必应", "https://www.bing.com", R.drawable.ic_bing),
            BrowserShortcut("知乎", "https://www.zhihu.com", R.drawable.ic_zhihu),
            BrowserShortcut("微博", "https://weibo.com", R.drawable.ic_weibo),
            BrowserShortcut("淘宝", "https://www.taobao.com", R.drawable.ic_taobao),
            BrowserShortcut("京东", "https://www.jd.com", R.drawable.ic_jd),
            BrowserShortcut("B站", "https://www.bilibili.com", R.drawable.ic_bilibili)
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
            holder.icon.setImageResource(shortcut.iconResId)
            holder.name.text = shortcut.name
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
    }

    /**
     * 检查是否为广告或恶意URL
     */
    private fun isAdOrMaliciousUrl(url: String): Boolean {
        val adKeywords = listOf(
            "googleads", "googlesyndication", "doubleclick", "adsystem",
            "amazon-adsystem", "facebook.com/tr", "google-analytics",
            "baidu.com/cpro", "pos.baidu.com", "cbjs.baidu.com",
            "union.360.cn", "tanx.com", "alimama.com", "taobao.com/go/act",
            "download", "install", "apk", "exe", "setup",
            "popup", "alert", "confirm", "prompt"
        )

        val lowerUrl = url.lowercase()
        return adKeywords.any { keyword -> lowerUrl.contains(keyword) }
    }

    /**
     * 注入广告拦截JavaScript
     */
    private fun injectAdBlockingScript(webView: WebView?) {
        val adBlockScript = """
            javascript:(function() {
                // 移除常见广告元素
                var adSelectors = [
                    '[id*="ad"]', '[class*="ad"]', '[id*="banner"]', '[class*="banner"]',
                    '[id*="popup"]', '[class*="popup"]', '[id*="modal"]', '[class*="modal"]',
                    '.advertisement', '.ads', '.ad-container', '.banner-ad',
                    'iframe[src*="ads"]', 'iframe[src*="doubleclick"]'
                ];

                adSelectors.forEach(function(selector) {
                    var elements = document.querySelectorAll(selector);
                    elements.forEach(function(el) {
                        if (el && el.parentNode) {
                            el.parentNode.removeChild(el);
                        }
                    });
                });

                // 阻止弹窗
                window.alert = function() { return false; };
                window.confirm = function() { return false; };
                window.prompt = function() { return null; };

                // 移除倒计时广告
                var countdownElements = document.querySelectorAll('[id*="countdown"], [class*="countdown"]');
                countdownElements.forEach(function(el) {
                    if (el && el.parentNode) {
                        el.parentNode.removeChild(el);
                    }
                });
            })();
        """

        try {
            webView?.evaluateJavascript(adBlockScript, null)
        } catch (e: Exception) {
            Log.w(TAG, "注入广告拦截脚本失败: ${e.message}")
        }
    }

    /**
     * 显示浏览器主页
     */
    private fun showBrowserHome() {
        browserWebView.visibility = View.GONE
        browserHomeContent.visibility = View.VISIBLE
        browserSearchInput.setText("")
    }

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

                // 加载URL
                loadBrowserContent(url)

                Log.d(TAG, "浏览器搜索 - 输入: '$query', 是否URL: $isUrl, 搜索引擎: ${currentSearchEngine?.name}, 最终URL: $url")

            } catch (e: Exception) {
                Log.e(TAG, "浏览器搜索失败", e)
                Toast.makeText(this, "搜索失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 加载浏览器内容 - 修复搜索框更新逻辑
     */
    private fun loadBrowserContent(url: String) {
        try {
            Log.d(TAG, "开始加载URL: $url")

            // 在WebView中加载URL
            browserWebView.loadUrl(url)

            // 更新UI显示
            browserWebView.visibility = View.VISIBLE
            browserHomeContent.visibility = View.GONE

            // 不要立即更新搜索框，让WebViewClient的onPageFinished来处理
            // 这样可以避免将搜索关键词替换为URL

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

        // 清空现有列表
        browserPreviewEngineList.removeAllViews()

        // 添加搜索引擎到列表
        filteredEngines.forEach { engine ->
            val engineView = createBrowserEngineView(engine)
            browserPreviewEngineList.addView(engineView)
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
        engineIcon.setImageResource(engine.iconResId)
        engineName.text = engine.displayName
        engineDescription.text = engine.description

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

            Log.d(TAG, "选择搜索引擎: ${engine.name}, 查询: '$query', 是否URL: $isUrl, 最终URL: $url")
        }

        return engineView
    }
}