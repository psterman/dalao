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
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.adapter.TaskTemplateAdapter
import com.example.aifloatingball.data.SimpleTaskTemplates
import com.example.aifloatingball.model.PromptTemplate
import com.example.aifloatingball.model.PromptField
import com.example.aifloatingball.model.FieldType
import com.example.aifloatingball.model.UserPromptData
import com.example.aifloatingball.service.DualFloatingWebViewService
import com.example.aifloatingball.service.SimpleModeService
import com.example.aifloatingball.service.FloatingWindowService
import com.example.aifloatingball.service.DynamicIslandService
import com.example.aifloatingball.voice.VoicePromptBranchManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import androidx.appcompat.app.AppCompatDelegate
import android.provider.Settings
import android.os.Build

class SimpleModeActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "SimpleModeActivity"
    }
    
    // 界面状态
    private enum class UIState {
        TASK_SELECTION,    // 任务选择页面
        STEP_GUIDANCE,     // 步骤引导页面
        PROMPT_PREVIEW,    // Prompt预览页面
        VOICE,             // 语音页面
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
        promptBranchManager = VoicePromptBranchManager(this)
        
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
    
    private fun checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val hasPermission = Settings.canDrawOverlays(this)
            Log.d(TAG, "Overlay permission status: $hasPermission")
            if (!hasPermission) {
                Toast.makeText(this, "警告：应用没有悬浮窗权限", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "悬浮窗权限正常", Toast.LENGTH_SHORT).show()
            }
        } else {
            Log.d(TAG, "Android version < M, no overlay permission check needed")
            Toast.makeText(this, "无需权限检查（旧版本Android）", Toast.LENGTH_SHORT).show()
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
        window.statusBarColor = androidx.core.content.ContextCompat.getColor(this, 
            if (isDarkMode) R.color.simple_mode_status_bar_light else R.color.simple_mode_status_bar_light)
        window.navigationBarColor = androidx.core.content.ContextCompat.getColor(this,
            if (isDarkMode) R.color.simple_mode_navigation_bar_light else R.color.simple_mode_navigation_bar_light)
            
        // 更新根布局背景
        findViewById<View>(android.R.id.content).setBackgroundColor(
            androidx.core.content.ContextCompat.getColor(this, R.color.simple_mode_background_light))
            
        // 更新标题栏颜色
        updateHeaderColors()
        
        // 更新底部导航颜色
        updateBottomNavigationColors()
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
                1 -> false // 搜索tab
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
        
        // 初始化UI颜色
        updateUIColors()
        updateTabColors()
    }
    
    private fun setupBottomNavigation() {
        findViewById<LinearLayout>(R.id.tab_home)?.setOnClickListener {
            showTaskSelection()
        }
        
        findViewById<LinearLayout>(R.id.tab_search)?.setOnClickListener {
            // 直接启动DualFloatingWebViewService搜索，不启动搜索Activity
            val intent = Intent(this, DualFloatingWebViewService::class.java).apply {
                putExtra("window_count", settingsManager.getDefaultWindowCount())
            }
            startService(intent)
            finish()
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
            // 启动DualFloatingWebViewService进行搜索
            val intent = Intent(this, DualFloatingWebViewService::class.java).apply {
                putExtra("search_query", query)
                putExtra("window_count", 2)
            }
            startService(intent)
            finish()
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
        
        // 启动DualFloatingWebViewService进行搜索
        val intent = Intent(this, DualFloatingWebViewService::class.java).apply {
            putExtra("search_query", prompt)
            putExtra("engine_key", engineKey)
            putExtra("search_source", "simple_mode")
            putExtra("show_toolbar", true)
        }
        startService(intent)
        
        // 关闭简易模式
        finish()
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
        Log.d(TAG, "onBackPressed called, currentState: $currentState")
        
        // 如果用户在主界面按后退键，直接退出
        if (currentState == UIState.TASK_SELECTION) {
            Log.d(TAG, "在主界面按后退键，直接退出")
            closeSimpleMode() // 使用我们的关闭方法
            return
        }
        
        when (currentState) {
            UIState.STEP_GUIDANCE -> {
                if (currentStepIndex > 0) {
                    currentStepIndex--
                    setupCurrentStep()
                } else {
                    showTaskSelection()
                }
            }
            UIState.PROMPT_PREVIEW -> {
                showStepGuidance()
            }
            UIState.VOICE -> {
                // 停止语音识别并返回首页
                releaseSpeechRecognizer()
                showTaskSelection()
            }
            UIState.SETTINGS -> {
                showTaskSelection()
            }
            else -> {
                Log.d(TAG, "其他状态下按后退键，调用closeSimpleMode()")
                closeSimpleMode() // 使用我们的关闭方法
            }
        }
    }

    private fun showSettings() {
        currentState = UIState.SETTINGS
        taskSelectionLayout.visibility = View.GONE
        stepGuidanceLayout.visibility = View.GONE
        promptPreviewLayout.visibility = View.GONE
        voiceLayout.visibility = View.GONE
        settingsLayout.visibility = View.VISIBLE
        
        // 更新设置页面的状态
        updateSettingsUI()
        // 更新Tab颜色状态
        updateTabColors()
    }
    
    private fun setupSettingsPage() {
        // 显示模式选择器
        val displayModeOptions = arrayOf("简易模式", "悬浮球模式", "动态岛模式")
        val displayModeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, displayModeOptions)
        displayModeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        displayModeSpinner.adapter = displayModeAdapter
        
        // 窗口数量选择器
        val windowCountOptions = arrayOf("1", "2", "3", "4", "5", "6")
        val windowCountAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, windowCountOptions)
        windowCountAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        windowCountSpinner.adapter = windowCountAdapter
        
        // 主题模式选择器
        val themeModeOptions = arrayOf("跟随系统", "浅色模式", "深色模式")
        val themeModeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, themeModeOptions)
        themeModeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        themeModeSpinner.adapter = themeModeAdapter
        
        // 设置监听器
        setupSettingsListeners()
        
        // 设置点击事件
        setupSettingsClickEvents()
        
        // 设置应用版本信息
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            appVersionText.text = "版本 ${packageInfo.versionName}"
        } catch (e: Exception) {
            appVersionText.text = "版本未知"
        }
    }
    
    private fun setupSettingsListeners() {
        // 显示模式监听器
        displayModeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                when (position) {
                    0 -> {
                        settingsManager.setDisplayMode("simple_mode")
                        // 当前已经在简易模式，无需切换
                    }
                    1 -> {
                        settingsManager.setDisplayMode("floating_ball")
                        switchToFloatingBallMode()
                    }
                    2 -> {
                        settingsManager.setDisplayMode("dynamic_island")
                        switchToDynamicIslandMode()
                    }
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        
        // 窗口数量监听器
        windowCountSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val count = (position + 1)
                settingsManager.setDefaultWindowCount(count)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        
        // 主题模式监听器
        themeModeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val themeMode = when (position) {
                    0 -> SettingsManager.THEME_MODE_SYSTEM
                    1 -> SettingsManager.THEME_MODE_LIGHT
                    2 -> SettingsManager.THEME_MODE_DARK
                    else -> SettingsManager.THEME_MODE_SYSTEM
                }
                settingsManager.setThemeMode(themeMode)
                applyTheme() // 应用新的主题
                updateUIColors() // 更新界面颜色
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        
        // 剪贴板监控开关
        clipboardMonitorSwitch.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.setClipboardListenerEnabled(isChecked)
        }
        
        // 自动隐藏开关
        autoHideSwitch.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.setAutoHide(isChecked)
        }
        
        // AI模式开关
        aiModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.setDefaultAIMode(isChecked)
        }
        
        // 左手模式开关
        leftHandedSwitch.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.setLeftHandedMode(isChecked)
        }
        
        // 自动粘贴开关
        autoPasteSwitch.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.setAutoPasteEnabled(isChecked)
        }
        
        // 通知监听开关
        notificationListenerSwitch.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.putBoolean("enable_notification_listener", isChecked)
        }
        
        // 悬浮球透明度滑块
        ballAlphaSeekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    settingsManager.setBallAlpha(progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }
    
    private fun setupSettingsClickEvents() {
        // AI API设置
        aiApiSettingsItem.setOnClickListener {
            val intent = Intent(this, AIApiSettingsActivity::class.java)
            startActivity(intent)
        }
        
        // 搜索引擎设置
        searchEngineSettingsItem.setOnClickListener {
            val intent = Intent(this, SearchEngineSettingsActivity::class.java)
            startActivity(intent)
        }
        
        // 主提示词设置
        masterPromptSettingsItem.setOnClickListener {
            val intent = Intent(this, MasterPromptSettingsActivity::class.java)
            startActivity(intent)
        }
        
        // 应用搜索设置
        appSearchSettingsItem.setOnClickListener {
            val intent = Intent(this, com.example.aifloatingball.settings.AppSearchSettingsActivity::class.java)
            startActivity(intent)
        }
        
        // 权限管理
        permissionManagementItem.setOnClickListener {
            val intent = Intent(this, PermissionManagementActivity::class.java)
            startActivity(intent)
        }
        
        // 查看搜索历史
        viewSearchHistoryItem.setOnClickListener {
            val intent = Intent(this, SearchHistoryActivity::class.java)
            startActivity(intent)
        }
        
        // 新手入门指南
        onboardingGuideItem.setOnClickListener {
            val intent = Intent(this, com.example.aifloatingball.ui.onboarding.OnboardingActivity::class.java)
            startActivity(intent)
        }
    }
    
    private fun updateSettingsUI() {
        // 更新显示模式选择
        when (settingsManager.getDisplayMode()) {
            "simple_mode" -> displayModeSpinner.setSelection(0)
            "floating_ball" -> displayModeSpinner.setSelection(1)
            "dynamic_island" -> displayModeSpinner.setSelection(2)
        }
        
        // 更新窗口数量选择
        val windowCount = settingsManager.getDefaultWindowCount()
        if (windowCount <= 6) {
            windowCountSpinner.setSelection(windowCount - 1)
        }
        
        // 更新主题模式选择
        val themeMode = settingsManager.getThemeMode()
        val spinnerPosition = when (themeMode) {
            SettingsManager.THEME_MODE_LIGHT -> 1
            SettingsManager.THEME_MODE_DARK -> 2
            else -> 0 // THEME_MODE_SYSTEM
        }
        themeModeSpinner.setSelection(spinnerPosition)
        
        // 更新开关状态
        clipboardMonitorSwitch.isChecked = settingsManager.isClipboardListenerEnabled()
        autoHideSwitch.isChecked = settingsManager.getAutoHide()
        aiModeSwitch.isChecked = settingsManager.isDefaultAIMode()
        leftHandedSwitch.isChecked = settingsManager.isLeftHandedModeEnabled()
        autoPasteSwitch.isChecked = settingsManager.isAutoPasteEnabled()
        notificationListenerSwitch.isChecked = settingsManager.getBoolean("enable_notification_listener", false)
        
        // 更新透明度滑块
        ballAlphaSeekbar.progress = settingsManager.getBallAlpha()
    }
    
    private fun switchToFloatingBallMode() {
        // 启动悬浮球服务
        val intent = Intent(this, FloatingWindowService::class.java)
        startService(intent)
        
        // 关闭当前Activity
        finish()
    }
    
    private fun switchToDynamicIslandMode() {
        // 启动动态岛服务
        val intent = Intent(this, DynamicIslandService::class.java)
        startService(intent)
        
        // 关闭当前Activity
        finish()
    }

    private fun showVoice() {
        currentState = UIState.VOICE
        taskSelectionLayout.visibility = View.GONE
        stepGuidanceLayout.visibility = View.GONE
        promptPreviewLayout.visibility = View.GONE
        voiceLayout.visibility = View.VISIBLE
        settingsLayout.visibility = View.GONE
        
        setupVoicePage()
        // 更新Tab颜色状态
        updateTabColors()
    }
    
    private fun setupVoicePage() {
        // 麦克风长按和点击事件
        voiceMicContainer.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // 设置长按检测
                    longPressHandler.postDelayed(longPressRunnable, 500) // 500ms长按阈值
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    // 如果是长按状态，消费事件
                    isLongPress
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // 取消长按检测
                    longPressHandler.removeCallbacks(longPressRunnable)
                    
                    // 如果不是长按，则触发点击事件
                    if (!isLongPress) {
                        toggleVoiceRecognition()
                    }
                    
                    // 重置长按状态
                    isLongPress = false
                    true
                }
                else -> false
            }
        }
        
        // 清空按钮
        voiceClearButton.setOnClickListener {
            clearVoiceText()
        }
        
        // 搜索按钮 - 确保启用状态和点击事件正确设置
        voiceSearchButton.setOnClickListener {
            executeVoiceSearch()
        }
        
        // 手动输入文本时也启用搜索按钮
        voiceTextInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                voiceSearchButton.isEnabled = !s.isNullOrBlank()
            }
        })
    }
    
    /**
     * 显示提示分支选择界面
     */
    private fun showPromptBranches() {
        // 获取根视图
        val rootView = findViewById<ViewGroup>(android.R.id.content)
        
        // 创建并显示分支视图
        promptBranchManager.showBranchView(rootView, voiceTextInput)
        
        // 添加背景模糊效果
        applyBackgroundBlur(true)
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
            
            // 启动DualFloatingWebViewService进行搜索
            val intent = Intent(this, DualFloatingWebViewService::class.java).apply {
                putExtra("search_query", query)
                putExtra("window_count", settingsManager.getDefaultWindowCount())
            }
            startService(intent)
            finish()
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
        // 检查悬浮窗权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "需要悬浮窗权限才能最小化", Toast.LENGTH_SHORT).show()
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                data = android.net.Uri.parse("package:$packageName")
            }
            startActivity(intent)
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
} 