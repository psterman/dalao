package com.example.aifloatingball.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.text.InputType
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import android.view.HapticFeedbackConstants
import android.graphics.Typeface
import androidx.core.content.ContextCompat
import android.content.res.Configuration
import com.example.aifloatingball.R
import com.example.aifloatingball.SettingsManager
import com.example.aifloatingball.VoiceRecognitionActivity
import com.example.aifloatingball.model.FieldType
import com.example.aifloatingball.model.PromptField
import com.example.aifloatingball.model.PromptTemplate
import com.example.aifloatingball.model.UserPromptData
import kotlinx.coroutines.*

class SimpleModeService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var simpleModeView: View
    private lateinit var minimizedView: View
    private lateinit var settingsManager: SettingsManager
    private lateinit var windowParams: WindowManager.LayoutParams
    private lateinit var minimizedParams: WindowManager.LayoutParams
    private var isWindowVisible = false
    private var isMinimized = false

    private var isDragging = false
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var dragThreshold = 10f

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var currentTemplate: PromptTemplate? = null
    private var currentFieldIndex: Int = -1
    private var userPromptData: UserPromptData? = null
    private lateinit var gridLayout: GridLayout

    private val promptTemplates by lazy { initializePromptTemplates() }

    private val commandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.example.aifloatingball.ACTION_SEARCH_AND_DESTROY") {
                val query = intent.getStringExtra("search_query")
                if (!query.isNullOrEmpty()) {
                    settingsManager.setDisplayMode("floating_ball")
                    val serviceIntent = Intent(context, DualFloatingWebViewService::class.java).apply {
                        putExtras(intent.extras ?: Bundle())
                    }
                    context?.startService(serviceIntent)
                    stopSelf()
                }
            }
        }
    }

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF || intent?.action == "com.example.aifloatingball.ACTION_CLOSE_SIMPLE_MODE") {
                stopSelf()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        settingsManager = SettingsManager.getInstance(this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val commandFilter = IntentFilter("com.example.aifloatingball.ACTION_SEARCH_AND_DESTROY")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(commandReceiver, commandFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(commandReceiver, commandFilter)
        }

        val screenFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction("com.example.aifloatingball.ACTION_CLOSE_SIMPLE_MODE")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenOffReceiver, screenFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenOffReceiver, screenFilter)
        }

        createSimpleModeWindow()
        createMinimizedWindow()
    }

    private fun createSimpleModeWindow() {
        simpleModeView = LayoutInflater.from(this).inflate(R.layout.simple_mode_layout, null)
        windowParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }
        showWindow()
        setupViews()
    }

    private fun createMinimizedWindow() {
        minimizedView = LayoutInflater.from(this).inflate(R.layout.simple_mode_minimized, null)
        val displayMetrics = resources.displayMetrics
        minimizedParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = displayMetrics.widthPixels - 30
            y = displayMetrics.heightPixels / 2 - 30
        }
        setupMinimizedView()
    }

    private fun setupMinimizedView() {
        minimizedView.findViewById<LinearLayout>(R.id.minimized_layout).setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isDragging = false
                    initialX = minimizedParams.x
                    initialY = minimizedParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - initialTouchX
                    val deltaY = event.rawY - initialTouchY
                    if (!isDragging && (kotlin.math.abs(deltaX) > dragThreshold || kotlin.math.abs(deltaY) > dragThreshold)) {
                        isDragging = true
                    }
                    if (isDragging) {
                        minimizedParams.x = (initialX + deltaX).toInt()
                        minimizedParams.y = (initialY + deltaY).toInt()
                        windowManager.updateViewLayout(minimizedView, minimizedParams)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) restoreFromMinimized() else snapToEdge()
                    isDragging = false
                    true
                }
                else -> false
            }
        }
    }

    private fun snapToEdge() {
        minimizedParams.x = if (minimizedParams.x < resources.displayMetrics.widthPixels / 2) -20 else resources.displayMetrics.widthPixels - 20
        windowManager.updateViewLayout(minimizedView, minimizedParams)
    }

    private fun minimizeToEdge() {
        if (isMinimized) return
        if (isWindowVisible) {
            windowManager.removeView(simpleModeView)
            isWindowVisible = false
        }
        if (!minimizedView.isAttachedToWindow) {
            windowManager.addView(minimizedView, minimizedParams)
        }
        isMinimized = true
    }

    private fun restoreFromMinimized() {
        if (!isMinimized) return
        if (minimizedView.isAttachedToWindow) {
            windowManager.removeView(minimizedView)
        }
        isMinimized = false
        showWindow()
    }

    private fun setupViews() {
        gridLayout = simpleModeView.findViewById(R.id.grid_layout)
        val searchEditText = simpleModeView.findViewById<EditText>(R.id.searchEditText)
        
        simpleModeView.findViewById<ImageButton>(R.id.simple_mode_minimize_button).setOnClickListener { minimizeToEdge() }
        simpleModeView.findViewById<ImageButton>(R.id.simple_mode_close_button).setOnClickListener { stopSelf() }

        searchEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = searchEditText.text.toString().trim()
                if (query.isNotEmpty()) directSearch(query)
                true
            } else false
        }

        simpleModeView.findViewById<LinearLayout>(R.id.tab_home).setOnClickListener { resetToIntentGrid() }
        simpleModeView.findViewById<LinearLayout>(R.id.tab_search).setOnClickListener {
            searchEditText.requestFocus()
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(searchEditText, InputMethodManager.SHOW_IMPLICIT)
        }
        simpleModeView.findViewById<LinearLayout>(R.id.tab_voice).setOnClickListener {
            hideWindow()
            startActivity(Intent(this, VoiceRecognitionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        simpleModeView.findViewById<LinearLayout>(R.id.tab_profile).setOnClickListener {
            val profile = settingsManager.getPromptProfile()
            Toast.makeText(this, "当前画像: ${profile.name}", Toast.LENGTH_LONG).show()
        }

        showIntentGrid()
    }

    private fun resetToIntentGrid() {
        currentTemplate = null
        currentFieldIndex = -1
        userPromptData = null
        showIntentGrid()
    }

    private fun showIntentGrid() {
        gridLayout.removeAllViews()
        gridLayout.columnCount = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 4 else 3
        promptTemplates.values.forEach { template ->
            val gridItem = createGridItem(template.intentName, template.icon) {
                startPromptBuildingProcess(template)
            }
            gridLayout.addView(gridItem)
        }
    }

    private fun startPromptBuildingProcess(template: PromptTemplate) {
        currentTemplate = template
        currentFieldIndex = 0
        userPromptData = UserPromptData(template.intentId)
        showNextField()
    }

    private fun showNextField() {
        hideKeyboard(simpleModeView)
        val template = currentTemplate ?: return
        if (currentFieldIndex >= template.fields.size) {
            generateAndExecuteFinalPrompt()
            return
        }

        gridLayout.removeAllViews()
        val field = template.fields[currentFieldIndex]

        val questionView = TextView(this).apply {
            text = field.question
            textSize = 20f
            gravity = Gravity.CENTER
            setPadding(16, 32, 16, 32)
            setTextColor(Color.BLACK)
        }
        gridLayout.addView(questionView, GridLayout.LayoutParams().apply {
            columnSpec = GridLayout.spec(0, gridLayout.columnCount, 1f)
            width = 0
        })

        when (field.type) {
            FieldType.TEXT_INPUT -> renderTextInput()
            FieldType.SINGLE_CHOICE -> renderSingleChoice(field)
            FieldType.MULTIPLE_CHOICE -> renderMultiChoice(field)
        }
        renderNavigationButtons(field.isOptional)
    }

    private fun renderTextInput() {
        val editText = EditText(this).apply {
            id = R.id.dynamic_edit_text
            hint = "请在此输入..."
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 2
            gravity = Gravity.TOP
            setPadding(24, 24, 24, 24)
            background = ContextCompat.getDrawable(context, R.drawable.search_bar_background_simple)
        }
        gridLayout.addView(editText, GridLayout.LayoutParams().apply {
            columnSpec = GridLayout.spec(0, gridLayout.columnCount, 1f)
            width = 0
            height = GridLayout.LayoutParams.WRAP_CONTENT
            setMargins(48, 16, 48, 16)
        })
    }

    private fun renderSingleChoice(field: PromptField) {
        val radioGroup = RadioGroup(this).apply { id = R.id.dynamic_radio_group }
        field.options?.forEach { optionText ->
            radioGroup.addView(RadioButton(this).apply {
                text = optionText
                textSize = 18f
                setPadding(16, 16, 16, 16)
            })
        }
        gridLayout.addView(radioGroup, GridLayout.LayoutParams().apply {
            columnSpec = GridLayout.spec(0, gridLayout.columnCount, 1f)
            width = 0
            setMargins(48, 16, 48, 16)
        })
    }

    private fun renderMultiChoice(field: PromptField) {
        val container = LinearLayout(this).apply {
            id = R.id.dynamic_checkbox_container
            orientation = LinearLayout.VERTICAL
        }
        field.options?.forEach { optionText ->
            container.addView(CheckBox(this).apply {
                text = optionText
                textSize = 18f
                setPadding(16, 16, 16, 16)
            })
        }
        gridLayout.addView(container, GridLayout.LayoutParams().apply {
            columnSpec = GridLayout.spec(0, gridLayout.columnCount, 1f)
            width = 0
            setMargins(48, 16, 48, 16)
        })
    }

    private fun renderNavigationButtons(isOptional: Boolean) {
        val navContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(16, 32, 16, 16)
        }
        
        if (currentFieldIndex > 0) {
            navContainer.addView(Button(this).apply {
                text = "上一步"
                setOnClickListener { goBack() }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = 16 })
        }

        if (isOptional) {
            navContainer.addView(Button(this).apply {
                text = "跳过"
                setOnClickListener { skipField() }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = 16 })
        }

        val isLastField = currentFieldIndex == (currentTemplate?.fields?.size ?: 0) - 1
        navContainer.addView(Button(this).apply {
            text = if (isLastField) "完成" else "下一步"
            setTypeface(null, Typeface.BOLD)
            setOnClickListener { collectAndProceed() }
        })

        gridLayout.addView(navContainer, GridLayout.LayoutParams().apply {
            columnSpec = GridLayout.spec(0, gridLayout.columnCount, 1f)
            width = 0
        })
    }

    private fun collectAndProceed() {
        val template = currentTemplate ?: return
        val field = template.fields[currentFieldIndex]
        var data: Any? = null
        
        when (field.type) {
            FieldType.TEXT_INPUT -> {
                data = gridLayout.findViewById<EditText>(R.id.dynamic_edit_text).text.toString().trim()
            }
            FieldType.SINGLE_CHOICE -> {
                val radioGroup = gridLayout.findViewById<RadioGroup>(R.id.dynamic_radio_group)
                val selectedId = radioGroup.checkedRadioButtonId
                if (selectedId != -1) {
                    data = radioGroup.findViewById<RadioButton>(selectedId).text.toString()
                }
            }
            FieldType.MULTIPLE_CHOICE -> {
                val container = gridLayout.findViewById<LinearLayout>(R.id.dynamic_checkbox_container)
                val selected = (0 until container.childCount)
                    .mapNotNull { container.getChildAt(it) as? CheckBox }
                    .filter { it.isChecked }
                    .map { it.text.toString() }
                if (selected.isNotEmpty()) data = selected
            }
        }
        
        if (data == null || (data is String && data.isEmpty())) {
            if (!field.isOptional) {
                Toast.makeText(this, "此项为必填项", Toast.LENGTH_SHORT).show()
                return
            }
        } else {
             userPromptData?.collectedData?.put(field.id, data)
        }
        
        currentFieldIndex++
        showNextField()
    }

    private fun skipField() {
        currentFieldIndex++
        showNextField()
    }

    private fun goBack() {
        if (currentFieldIndex > 0) {
            currentFieldIndex--
            showNextField()
        } else {
            resetToIntentGrid()
        }
    }

    private fun generateAndExecuteFinalPrompt() {
        val template = currentTemplate ?: return
        val data = userPromptData?.collectedData ?: return
        
        val profile = settingsManager.getPromptProfile()
        var finalPrompt = "请基于以下设定为我提供信息：我的角色是“${profile.persona}”，我希望内容的语气是“${profile.tone}”，并且输出格式为“${profile.outputFormat}”。\n---\n"
        
        var promptCore = template.finalPromptFormat
        data.forEach { (key, value) ->
            val replacement = when (value) {
                is List<*> -> value.joinToString(", ")
                else -> value.toString()
            }
            promptCore = promptCore.replace("{${key}}", replacement, ignoreCase = true)
        }

        promptCore = promptCore.replace(Regex("\\{\\w+\\}"), "").trim()
        finalPrompt += promptCore

        directSearch(finalPrompt, "智能引导", template.recommendedEngines.toTypedArray())
        resetToIntentGrid()
    }

    private fun directSearch(query: String, source: String = "直接搜索", engines: Array<String> = arrayOf("deepseek", "google", "bing")) {
        Toast.makeText(this, "正在为您搜索...", Toast.LENGTH_SHORT).show()
        val intent = Intent(this, DualFloatingWebViewService::class.java).apply {
            putExtra("search_query", query)
            putExtra("search_engines", engines)
            putExtra("search_source", source)
            putExtra("show_toolbar", true)
        }
        startService(intent)
        minimizeToEdge()
    }

    private fun createGridItem(title: String, icon: String, onClick: () -> Unit): View {
        val gridItemView = LayoutInflater.from(this).inflate(R.layout.simple_mode_grid_item, gridLayout, false) as LinearLayout
        gridItemView.findViewById<TextView>(R.id.grid_item_icon).text = icon
        gridItemView.findViewById<TextView>(R.id.grid_item_title).text = title
        gridItemView.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            onClick()
        }
        gridItemView.layoutParams = GridLayout.LayoutParams().apply {
            width = 0
            height = GridLayout.LayoutParams.WRAP_CONTENT
            columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f)
            rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f)
            setMargins(16, 16, 16, 16)
        }
        return gridItemView
    }

    private fun hideKeyboard(view: View) {
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun showWindow() {
        if (!isWindowVisible && !simpleModeView.isAttachedToWindow) {
            windowManager.addView(simpleModeView, windowParams)
            isWindowVisible = true
        }
    }

    private fun hideWindow() {
        if (isWindowVisible && simpleModeView.isAttachedToWindow) {
            windowManager.removeView(simpleModeView)
            isWindowVisible = false
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        super.onDestroy()
        hideWindow()
        if (minimizedView.isAttachedToWindow) {
            windowManager.removeView(minimizedView)
        }
        unregisterReceiver(commandReceiver)
        unregisterReceiver(screenOffReceiver)
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        checkOrientation()
    }
    
    private fun checkOrientation() {
        if (::gridLayout.isInitialized && currentTemplate == null) {
            gridLayout.columnCount = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 4 else 3
        }
    }

    private fun initializePromptTemplates(): Map<String, PromptTemplate> {
        return mapOf(
            "understand" to PromptTemplate(
                intentId = "understand", intentName = "我想了解", icon = "🔍", description = "深入了解一个概念、主题或事件。",
                fields = listOf(
                    PromptField("topic", "您想了解关于什么？", FieldType.TEXT_INPUT),
                    PromptField("aspects", "有没有特别想关注的方面？(可选)", FieldType.TEXT_INPUT, isOptional = true),
                    PromptField("depth", "希望了解的深度是？", FieldType.SINGLE_CHOICE, options = listOf("入门介绍", "深入细节", "专家视角"))
                ),
                finalPromptFormat = "请以“{depth}”的水平，帮我介绍一下“{topic}”。如果可能，请侧重于“{aspects}”方面进行说明。",
                recommendedEngines = listOf("deepseek", "zhihu", "wikipedia")
            ),
            "solve" to PromptTemplate(
                intentId = "solve", intentName = "我要解决", icon = "🔧", description = "找到一个具体问题的解决方案。",
                fields = listOf(
                    PromptField("problem", "您遇到了什么问题？", FieldType.TEXT_INPUT),
                    PromptField("context", "这个问题发生的背景或环境是？(可选)", FieldType.TEXT_INPUT, isOptional = true),
                    PromptField("goal", "您期望达到的理想结果是？", FieldType.TEXT_INPUT)
                ),
                finalPromptFormat = "我遇到了一个问题：“{problem}”。具体情况是：“{context}”。我的目标是：“{goal}”。请提供详细的解决方案步骤。",
                recommendedEngines = listOf("deepseek", "google", "bing")
            ),
            "choose" to PromptTemplate(
                intentId = "choose", intentName = "我需选择", icon = "⚖️", description = "在多个选项之间做出决策。",
                fields = listOf(
                    PromptField("options", "有哪些选项需要比较？(请用逗号分隔)", FieldType.TEXT_INPUT),
                    PromptField("criteria", "您做选择时主要看重哪些标准？(如价格、性能)", FieldType.TEXT_INPUT),
                    PromptField("priority", "哪个标准最重要？(可选)", FieldType.TEXT_INPUT, isOptional = true)
                ),
                finalPromptFormat = "我正在“{options}”之间做选择。我主要的评判标准是“{criteria}”，其中“{priority}”是优先考虑的。请帮我分析各项优劣并给出建议。",
                recommendedEngines = listOf("deepseek", "douban", "zhihu")
            ),
            "suggest" to PromptTemplate(
                intentId = "suggest", intentName = "求建议", icon = "💡", description = "为一个想法或情况寻求建议。",
                fields = listOf(
                    PromptField("situation", "请描述您的情况或想法。", FieldType.TEXT_INPUT),
                    PromptField("goal", "您希望通过建议达到什么目的？(可选)", FieldType.TEXT_INPUT, isOptional = true)
                ),
                finalPromptFormat = "我的情况是：“{situation}”。我的目标是：“{goal}”。请就此给我一些建议。",
                recommendedEngines = listOf("deepseek", "zhihu")
            ),
            "plan" to PromptTemplate(
                intentId = "plan", intentName = "做计划", icon = "📋", description = "为一个活动、项目或目标制定计划。",
                fields = listOf(
                    PromptField("activity", "您想为哪个活动或目标做计划？", FieldType.TEXT_INPUT),
                    PromptField("duration", "计划的时间跨度是多久？", FieldType.TEXT_INPUT),
                    PromptField("participants", "参与人有哪些？(可选)", FieldType.TEXT_INPUT, isOptional = true)
                ),
                finalPromptFormat = "请帮我为“{activity}”制定一个为期“{duration}”的详细计划。计划需要考虑“{participants}”。",
                recommendedEngines = listOf("deepseek", "google")
            ),
            "create" to PromptTemplate(
                intentId = "create", intentName = "要创作", icon = "🎨", description = "生成文本、诗歌、代码等创意内容。",
                fields = listOf(
                    PromptField("type", "您想创作什么类型的内容？", FieldType.SINGLE_CHOICE, options = listOf("诗歌", "短文", "邮件", "代码")),
                    PromptField("topic", "创作的主题是什么？", FieldType.TEXT_INPUT),
                    PromptField("style", "希望的风格是？(如风趣、正式，可选)", FieldType.TEXT_INPUT, isOptional = true)
                ),
                finalPromptFormat = "请帮我创作一篇关于“{topic}”的“{type}”，风格希望是“{style}”。",
                recommendedEngines = listOf("deepseek")
            ),
             "help" to PromptTemplate(
                intentId = "help", intentName = "寻帮助", icon = "🤝", description = "寻求生活、技能等方面的具体帮助。",
                fields = listOf(
                    PromptField("task", "您需要什么帮助？(如：学做菜，修东西)", FieldType.TEXT_INPUT),
                    PromptField("details", "能提供更多细节吗？(可选)", FieldType.TEXT_INPUT, isOptional = true)
                ),
                finalPromptFormat = "我需要“{task}”方面的帮助，具体来说是“{details}”。请提供教程或指南。",
                recommendedEngines = listOf("google", "bilibili", "douyin")
            ),
            "discuss" to PromptTemplate(
                intentId = "discuss", intentName = "想讨论", icon = "💬", description = "与AI就某个话题进行深入探讨。",
                fields = listOf(
                    PromptField("topic", "您想讨论什么话题？", FieldType.TEXT_INPUT),
                    PromptField("viewpoint", "您自己有什么初步看法吗？(可选)", FieldType.TEXT_INPUT, isOptional = true)
                ),
                finalPromptFormat = "我想和你深入讨论一下“{topic}”。我的初步看法是：“{viewpoint}”。请分享你的观点，并提出一些有深度的问题。",
                recommendedEngines = listOf("deepseek")
            ),
            "analyze" to PromptTemplate(
                intentId = "analyze", intentName = "要分析", icon = "📊", description = "分析数据、文本或情况。",
                fields = listOf(
                    PromptField("data", "请输入您要分析的文本或数据。", FieldType.TEXT_INPUT),
                    PromptField("goal", "您希望从分析中得到什么？(如：总结要点、发现趋势)", FieldType.TEXT_INPUT)
                ),
                finalPromptFormat = "请帮我分析以下内容：\n---\n{data}\n---\n我希望从中“{goal}”。",
                recommendedEngines = listOf("deepseek")
            )
        )
    }
} 