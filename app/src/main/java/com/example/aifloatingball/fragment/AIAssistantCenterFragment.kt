package com.example.aifloatingball.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.aifloatingball.R
import com.example.aifloatingball.SettingsManager

/**
 * AI助手中心Fragment基类
 */
abstract class AIAssistantCenterFragment : Fragment() {
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(getLayoutResId(), container, false)
    }
    
    abstract fun getLayoutResId(): Int
}

/**
 * 基础信息Fragment
 */
class BasicInfoFragment : AIAssistantCenterFragment() {
    private lateinit var settingsManager: SettingsManager
    private lateinit var aiSearchModeSwitch: com.google.android.material.switchmaterial.SwitchMaterial
    
    override fun getLayoutResId(): Int = R.layout.ai_assistant_basic_info_fragment
    
    override fun onViewCreated(view: android.view.View, savedInstanceState: android.os.Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        settingsManager = SettingsManager.getInstance(requireContext())
        aiSearchModeSwitch = view.findViewById(R.id.ai_search_mode_switch)
        
        // 加载当前设置
        loadCurrentSettings()
        
        // 设置开关监听器
        setupSwitchListener()
    }
    
    private fun loadCurrentSettings() {
        val isAiSearchModeEnabled = settingsManager.getIsAIMode()
        aiSearchModeSwitch.isChecked = isAiSearchModeEnabled
    }
    
    private fun setupSwitchListener() {
        aiSearchModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.setIsAIMode(isChecked)
            android.widget.Toast.makeText(
                requireContext(),
                if (isChecked) "已启用默认AI搜索模式" else "已关闭默认AI搜索模式",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }
}

/**
 * 扩展配置Fragment
 */
class ExtendedConfigFragment : AIAssistantCenterFragment() {
    override fun getLayoutResId(): Int = R.layout.ai_assistant_extended_config_fragment
}

/**
 * AI行为Fragment
 */
class AIBehaviorFragment : AIAssistantCenterFragment() {
    override fun getLayoutResId(): Int = R.layout.ai_assistant_behavior_fragment
}

/**
 * 个性化Fragment
 */
class PersonalizationFragment : AIAssistantCenterFragment() {
    private lateinit var settingsManager: SettingsManager
    
    // 主题相关控件
    private lateinit var lightThemeRadio: android.widget.RadioButton
    private lateinit var darkThemeRadio: android.widget.RadioButton
    private lateinit var autoThemeRadio: android.widget.RadioButton
    private lateinit var themeGroup: android.widget.RadioGroup
    
    // 字体大小控件
    private lateinit var fontSizeSeekBar: android.widget.SeekBar
    
    // 通知设置控件
    private lateinit var pushNotificationSwitch: com.google.android.material.switchmaterial.SwitchMaterial
    private lateinit var soundNotificationSwitch: com.google.android.material.switchmaterial.SwitchMaterial
    private lateinit var vibrationNotificationSwitch: com.google.android.material.switchmaterial.SwitchMaterial
    
    // 隐私设置控件
    private lateinit var dataCollectionSwitch: com.google.android.material.switchmaterial.SwitchMaterial
    private lateinit var anonymizationSwitch: com.google.android.material.switchmaterial.SwitchMaterial
    
    override fun getLayoutResId(): Int = R.layout.ai_assistant_personalization_fragment
    
    override fun onViewCreated(view: android.view.View, savedInstanceState: android.os.Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        settingsManager = SettingsManager.getInstance(requireContext())
        setupViews(view)
        loadCurrentSettings()
        setupListeners()
    }
    
    private fun setupViews(view: android.view.View) {
        // 主题设置
        themeGroup = view.findViewById(R.id.theme_group)
        lightThemeRadio = view.findViewById(R.id.light_theme_radio)
        darkThemeRadio = view.findViewById(R.id.dark_theme_radio)
        autoThemeRadio = view.findViewById(R.id.auto_theme_radio)
        
        // 字体大小
        fontSizeSeekBar = view.findViewById(R.id.font_size_seekbar)
        
        // 通知设置
        pushNotificationSwitch = view.findViewById(R.id.push_notification_switch)
        soundNotificationSwitch = view.findViewById(R.id.sound_notification_switch)
        vibrationNotificationSwitch = view.findViewById(R.id.vibration_notification_switch)
        
        // 隐私设置
        dataCollectionSwitch = view.findViewById(R.id.data_collection_switch)
        anonymizationSwitch = view.findViewById(R.id.anonymization_switch)
    }
    
    private fun loadCurrentSettings() {
        // 加载主题设置
        val currentTheme = settingsManager.getThemeModeAsString()
        when (currentTheme) {
            "light" -> lightThemeRadio.isChecked = true
            "dark" -> darkThemeRadio.isChecked = true
            "auto" -> autoThemeRadio.isChecked = true
            else -> autoThemeRadio.isChecked = true
        }
        
        // 加载字体大小设置
        val fontSize = settingsManager.getFontSize()
        fontSizeSeekBar.progress = fontSize
        
        // 加载通知设置
        pushNotificationSwitch.isChecked = settingsManager.getPushNotificationEnabled()
        soundNotificationSwitch.isChecked = settingsManager.getSoundNotificationEnabled()
        vibrationNotificationSwitch.isChecked = settingsManager.getVibrationNotificationEnabled()
        
        // 加载隐私设置
        dataCollectionSwitch.isChecked = settingsManager.getDataCollectionEnabled()
        anonymizationSwitch.isChecked = settingsManager.getAnonymizationEnabled()
    }
    
    private fun setupListeners() {
        // 主题选择监听
        themeGroup.setOnCheckedChangeListener { _, checkedId ->
            val theme = when (checkedId) {
                R.id.light_theme_radio -> "light"
                R.id.dark_theme_radio -> "dark"
                R.id.auto_theme_radio -> "auto"
                else -> "auto"
            }
            settingsManager.setThemeModeFromString(theme)
            
            // 立即应用主题变化
            val themeMode = when (theme) {
                "light" -> SettingsManager.THEME_MODE_LIGHT
                "dark" -> SettingsManager.THEME_MODE_DARK
                "auto" -> SettingsManager.THEME_MODE_SYSTEM
                else -> SettingsManager.THEME_MODE_SYSTEM
            }
            
            // 应用主题到当前Activity
            val targetNightMode = when (themeMode) {
                SettingsManager.THEME_MODE_LIGHT -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
                SettingsManager.THEME_MODE_DARK -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
                else -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(targetNightMode)
            
            // 重新创建Activity以应用主题变化
            activity?.recreate()
            
            android.widget.Toast.makeText(requireContext(), "主题设置已保存", android.widget.Toast.LENGTH_SHORT).show()
        }
        
        // 字体大小监听
        fontSizeSeekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    settingsManager.setFontSize(progress)
                }
            }
            
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })
        
        // 通知设置监听
        pushNotificationSwitch.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.setPushNotificationEnabled(isChecked)
            android.widget.Toast.makeText(requireContext(), 
                if (isChecked) "推送通知已启用" else "推送通知已关闭", 
                android.widget.Toast.LENGTH_SHORT).show()
        }
        
        soundNotificationSwitch.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.setSoundNotificationEnabled(isChecked)
            android.widget.Toast.makeText(requireContext(), 
                if (isChecked) "声音提醒已启用" else "声音提醒已关闭", 
                android.widget.Toast.LENGTH_SHORT).show()
        }
        
        vibrationNotificationSwitch.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.setVibrationNotificationEnabled(isChecked)
            android.widget.Toast.makeText(requireContext(), 
                if (isChecked) "震动提醒已启用" else "震动提醒已关闭", 
                android.widget.Toast.LENGTH_SHORT).show()
        }
        
        // 隐私设置监听
        dataCollectionSwitch.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.setDataCollectionEnabled(isChecked)
            android.widget.Toast.makeText(requireContext(), 
                if (isChecked) "数据收集已启用" else "数据收集已关闭", 
                android.widget.Toast.LENGTH_SHORT).show()
        }
        
        anonymizationSwitch.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.setAnonymizationEnabled(isChecked)
            android.widget.Toast.makeText(requireContext(), 
                if (isChecked) "匿名化处理已启用" else "匿名化处理已关闭", 
                android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}

/**
 * 任务Fragment
 */
class TaskFragment : AIAssistantCenterFragment() {
    private lateinit var taskRecyclerView: androidx.recyclerview.widget.RecyclerView
    private lateinit var taskAdapter: com.example.aifloatingball.adapter.TaskTemplateAdapter
    private lateinit var searchInput: android.widget.EditText
    private lateinit var searchButton: android.widget.ImageButton
    
    override fun getLayoutResId(): Int = R.layout.ai_assistant_task_fragment
    
    override fun onViewCreated(view: android.view.View, savedInstanceState: android.os.Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupViews(view)
        setupRecyclerView()
        setupSearch()
    }
    
    private fun setupViews(view: android.view.View) {
        taskRecyclerView = view.findViewById(R.id.ai_task_recycler_view)
        searchInput = view.findViewById(R.id.task_direct_search_input)
        searchButton = view.findViewById(R.id.task_direct_search_button)
    }
    
    private fun setupRecyclerView() {
        // 使用SimpleTaskTemplates的数据
        val templates = com.example.aifloatingball.data.SimpleTaskTemplates.templates
        taskAdapter = com.example.aifloatingball.adapter.TaskTemplateAdapter(templates) { template ->
            onTaskSelected(template)
        }
        
        // 设置网格布局管理器
        val layoutManager = androidx.recyclerview.widget.GridLayoutManager(requireContext(), 2)
        taskRecyclerView.layoutManager = layoutManager
        taskRecyclerView.adapter = taskAdapter
        
        // 添加分割线
        val decoration = androidx.recyclerview.widget.DividerItemDecoration(requireContext(), layoutManager.orientation)
        taskRecyclerView.addItemDecoration(decoration)
    }
    
    private fun setupSearch() {
        searchButton.setOnClickListener {
            val query = searchInput.text.toString().trim()
            if (query.isNotEmpty()) {
                performSearch(query)
            } else {
                android.widget.Toast.makeText(requireContext(), "请输入搜索内容", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        
        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                val query = searchInput.text.toString().trim()
                if (query.isNotEmpty()) {
                    performSearch(query)
                }
                true
            } else {
                false
            }
        }
    }
    
    private fun onTaskSelected(template: com.example.aifloatingball.model.PromptTemplate) {
        // 处理任务选择
        android.widget.Toast.makeText(requireContext(), "选择了任务: ${template.intentName}", android.widget.Toast.LENGTH_SHORT).show()
        
        // 这里可以添加跳转到任务详情页面的逻辑
        // 或者直接开始任务流程
    }
    
    private fun performSearch(query: String) {
        // 执行搜索逻辑
        android.widget.Toast.makeText(requireContext(), "搜索: $query", android.widget.Toast.LENGTH_SHORT).show()
        
        // 这里可以添加实际的搜索功能
        // 比如过滤任务模板或调用搜索API
    }
}
