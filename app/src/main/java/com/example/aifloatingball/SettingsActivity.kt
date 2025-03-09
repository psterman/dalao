package com.example.aifloatingball

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.widget.Switch
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.*
import com.example.aifloatingball.model.AISearchEngine
import com.example.aifloatingball.model.SearchEngine

class SettingsActivity : AppCompatActivity() {
    
    private lateinit var settingsManager: SettingsManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
        settingsManager = SettingsManager.getInstance(this)
        
        // 初始化左手模式开关
        val switchLeftHandedMode = findViewById<Switch>(R.id.switchLeftHandedMode)
        switchLeftHandedMode.isChecked = settingsManager.isLeftHandedMode
        
        // 监听左手模式开关变化
        switchLeftHandedMode.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.isLeftHandedMode = isChecked
            // 发送广播通知其他组件更新布局
            sendBroadcast(Intent(ACTION_SETTINGS_CHANGED).apply {
                putExtra(EXTRA_LEFT_HANDED_MODE_CHANGED, true)
            })
        }
        
        // 只在第一次创建时添加Fragment
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, SettingsFragment())
                .commit()
        }
        
        // 设置返回按钮
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    class SettingsFragment : PreferenceFragmentCompat(), SharedPreferences.OnSharedPreferenceChangeListener {
        private lateinit var settingsManager: SettingsManager
        
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)
            settingsManager = SettingsManager.getInstance(requireContext())

            // 处理搜索引擎排序设置
            findPreference<Preference>("search_engine_settings")?.setOnPreferenceClickListener {
                startActivity(Intent(requireContext(), SearchEngineSettingsActivity::class.java))
                true
            }

            // 初始化悬浮球大小设置
            findPreference<SeekBarPreference>("floating_ball_size")?.apply {
                value = settingsManager.getFloatingBallSize()
                setOnPreferenceChangeListener { _, newValue ->
                    settingsManager.setFloatingBallSize(newValue as Int)
                    // 通知FloatingWindowService更新悬浮球大小
                    val intent = Intent(requireContext(), FloatingWindowService::class.java).apply {
                        action = "UPDATE_BALL_SIZE"
                        putExtra("size", newValue)
                    }
                    requireContext().startService(intent)
                    true
                }
            }

            // 初始化布局主题设置
            findPreference<ListPreference>("layout_theme")?.apply {
                value = settingsManager.getLayoutTheme()
                setOnPreferenceChangeListener { _, newValue ->
                    settingsManager.setLayoutTheme(newValue as String)
                    // 发送广播通知SearchActivity更新布局
                    val intent = Intent("com.example.aifloatingball.LAYOUT_THEME_CHANGED")
                    requireContext().sendBroadcast(intent)
                    true
                }
            }

            // 初始化主题设置
            findPreference<ListPreference>("theme_mode")?.apply {
                value = settingsManager.getThemeMode()
                setOnPreferenceChangeListener { _, newValue ->
                    settingsManager.setThemeMode(newValue as String)
                    updateTheme(newValue)
                    true
                }
            }

            // 初始化隐私模式设置
            findPreference<SwitchPreferenceCompat>("privacy_mode")?.apply {
                isChecked = settingsManager.getPrivacyMode()
                setOnPreferenceChangeListener { _, newValue ->
                    settingsManager.setPrivacyMode(newValue as Boolean)
                    true
                }
            }

            // 初始化自启动设置
            findPreference<SwitchPreferenceCompat>("auto_start")?.apply {
                isChecked = settingsManager.getAutoStart()
                setOnPreferenceChangeListener { _, newValue ->
                    settingsManager.setAutoStart(newValue as Boolean)
                    true
                }
            }

            // 处理版本信息
            findPreference<Preference>("app_version")?.apply {
                summary = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0).versionName
            }

            // 处理检查更新
            findPreference<Preference>("check_update")?.setOnPreferenceClickListener {
                // TODO: 实现检查更新逻辑
                true
            }

            // 左手模式开关
            findPreference<SwitchPreferenceCompat>(PREF_LEFT_HANDED_MODE)?.setOnPreferenceChangeListener { _, _ ->
                val intent = Intent(ACTION_SETTINGS_CHANGED).apply {
                    putExtra(EXTRA_LEFT_HANDED_MODE_CHANGED, true)
                }
                requireContext().sendBroadcast(intent)
                true
            }

            // 默认搜索模式
            findPreference<SwitchPreferenceCompat>(PREF_DEFAULT_SEARCH_MODE)?.apply {
                // 确保开关显示正确的初始状态
                isChecked = settingsManager.isDefaultAIMode()
                
                setOnPreferenceChangeListener { _, newValue ->
                    // 当用户切换默认搜索模式（AI或普通）时，保存设置并通知其他组件
                    val isAIMode = newValue as Boolean
                    
                    // 记录切换
                    Log.d("SettingsActivity", "用户切换默认搜索模式: ${if (isAIMode) "AI模式" else "普通模式"}")
                    
                    // 保存设置（使用公共方法而不是直接访问私有字段）
                    settingsManager.setDefaultAIMode(isAIMode)
                    
                    // 再次检查设置是否保存成功
                    val saved = settingsManager.isDefaultAIMode()
                    Log.d("SettingsActivity", "设置保存后的状态: ${if (saved) "AI模式" else "普通模式"}")
                    
                    // 提示用户如何查看更改
                    Toast.makeText(requireContext(), 
                        if (isAIMode) "已设置为AI搜索模式，请重新打开搜索或点击下方按钮应用更改" 
                        else "已设置为普通搜索模式，请重新打开搜索或点击下方按钮应用更改", 
                        Toast.LENGTH_LONG
                    ).show()
                    true
                }
            }
            
            // 添加一个应用更改的按钮
            findPreference<Preference>("apply_search_mode_changes")?.apply {
                isVisible = true
                title = "应用搜索模式更改"
                summary = "点击此处立即应用搜索模式更改"
                
                setOnPreferenceClickListener {
                    // 创建启动SearchActivity的Intent - 更新为aifloatingball包
                    val intent = Intent()
                    intent.setClassName(requireContext().packageName, "com.example.aifloatingball.SearchActivity")
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    intent.putExtra("refresh_mode", true)
                    intent.putExtra("is_ai_mode", settingsManager.isDefaultAIMode())
                    
                    try {
                        Log.d("SettingsActivity", "正在启动SearchActivity刷新搜索模式...")
                        requireContext().startActivity(intent)
                        Toast.makeText(requireContext(), "正在刷新搜索界面...", Toast.LENGTH_SHORT).show()
                        true
                    } catch (e: Exception) {
                        Log.e("SettingsActivity", "启动SearchActivity失败", e)
                        Toast.makeText(requireContext(), "应用更改失败: ${e.message}", Toast.LENGTH_SHORT).show()
                        false
                    }
                }
            }

            // 默认搜索引擎
            setupDefaultSearchEnginePreference()
        }

        private fun updateTheme(mode: String) {
            val nightMode = when (mode) {
                "light" -> AppCompatDelegate.MODE_NIGHT_NO
                "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            AppCompatDelegate.setDefaultNightMode(nightMode)
            requireActivity().recreate()
        }

        private fun setupDefaultSearchEnginePreference() {
            val defaultSearchPref = findPreference<ListPreference>(PREF_DEFAULT_SEARCH_ENGINE)
            val defaultSearchModePref = findPreference<SwitchPreferenceCompat>(PREF_DEFAULT_SEARCH_MODE)
            
            // 初始化所有搜索引擎列表（包括AI和普通搜索引擎）
            updateAllSearchEngineList()
            
            // 设置搜索引擎选择监听器
            defaultSearchPref?.apply {
                setOnPreferenceChangeListener { _, newValue ->
                    try {
                        // 解析选择的搜索引擎信息：引擎名称、URL
                        val engineInfo = (newValue as String).split("|")
                        val engineName = engineInfo[0]
                        // 保存设置
                        settingsManager.putString(PREF_DEFAULT_SEARCH_ENGINE, newValue)
                        
                        Toast.makeText(requireContext(), "已设置 $engineName 为默认搜索引擎", Toast.LENGTH_SHORT).show()
                        true
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "设置默认搜索引擎失败: ${e.message}", Toast.LENGTH_SHORT).show()
                        false
                    }
                }
            }
            
            // 监听默认AI模式变化
            defaultSearchModePref?.apply {
                setOnPreferenceChangeListener { _, newValue ->
                    val useAIMode = newValue as Boolean
                    // 仅保存AI模式设置，不改变搜索引擎列表
                    settingsManager.putBoolean(PREF_DEFAULT_SEARCH_MODE, useAIMode)
                    Toast.makeText(requireContext(), 
                        if (useAIMode) "已设置为AI搜索模式" else "已设置为普通搜索模式", 
                        Toast.LENGTH_SHORT
                    ).show()
                    true
                }
            }
        }

        private fun updateAllSearchEngineList() {
            val defaultSearchPref = findPreference<ListPreference>(PREF_DEFAULT_SEARCH_ENGINE)
            
            // 合并AI和普通搜索引擎列表
            val allEngines = mutableListOf<Any>()
            
            // 添加AI搜索引擎
            try {
                val aiEngines = AISearchEngine.DEFAULT_AI_ENGINES
                if (aiEngines.isNotEmpty()) {
                    allEngines.addAll(aiEngines)
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "加载AI搜索引擎失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            
            // 添加普通搜索引擎
            // 确保功能主页在普通搜索引擎列表的第一位
            val homeEngine = SearchActivity.NORMAL_SEARCH_ENGINES.find { it.name == "功能主页" }
            if (homeEngine != null) {
                allEngines.add(homeEngine)
                allEngines.addAll(SearchActivity.NORMAL_SEARCH_ENGINES.filter { it.name != "功能主页" })
            } else {
                allEngines.addAll(SearchActivity.NORMAL_SEARCH_ENGINES)
            }

            // 准备搜索引擎的显示名称和值
            val engineNames = ArrayList<CharSequence>()
            val engineValues = ArrayList<CharSequence>()
            
            // 填充名称和值列表
            allEngines.forEach { engine ->
                when (engine) {
                    is AISearchEngine -> {
                        engineNames.add("${engine.name} (AI)")
                        engineValues.add("${engine.name}|${engine.url}")
                    }
                    is SearchEngine -> {
                        engineNames.add(engine.name)
                        engineValues.add("${engine.name}|${engine.url}")
                    }
                }
            }

            defaultSearchPref?.apply {
                entries = engineNames.toTypedArray()
                entryValues = engineValues.toTypedArray()
                
                // 获取当前选中的搜索引擎
                val currentValue = value
                if (currentValue == null || !engineValues.contains(currentValue)) {
                    // 如果当前没有选中的搜索引擎或者选中的搜索引擎不在列表中
                    // 选择列表中的第一个搜索引擎
                    if (engineValues.isNotEmpty()) {
                        value = engineValues[0].toString()
                        // 通知用户搜索引擎已更改
                        if (allEngines.isNotEmpty()) {
                            val engineName = when (val firstEngine = allEngines.first()) {
                                is AISearchEngine -> "${firstEngine.name} (AI)"
                                is SearchEngine -> firstEngine.name
                                else -> "未知搜索引擎"
                            }
                            Toast.makeText(requireContext(), 
                                "已将默认搜索引擎设置为: $engineName", 
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        }

        override fun onResume() {
            super.onResume()
            preferenceScreen.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
        }

        override fun onPause() {
            super.onPause()
            preferenceScreen.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
        }

        override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
            when (key) {
                "floating_ball_size" -> {
                    val size = sharedPreferences?.getInt(key, 50) ?: 50
                    settingsManager.setFloatingBallSize(size)
                    val intent = Intent(requireContext(), FloatingWindowService::class.java).apply {
                        action = "UPDATE_BALL_SIZE"
                        putExtra("size", size)
                    }
                    requireContext().startService(intent)
                }
                "theme_mode" -> {
                    val mode = sharedPreferences?.getString(key, "system") ?: "system"
                    settingsManager.setThemeMode(mode)
                    updateTheme(mode)
                }
                "privacy_mode" -> {
                    val enabled = sharedPreferences?.getBoolean(key, false) ?: false
                    settingsManager.setPrivacyMode(enabled)
                }
                "auto_start" -> {
                    val enabled = sharedPreferences?.getBoolean(key, false) ?: false
                    settingsManager.setAutoStart(enabled)
                }
            }
        }
    }

    companion object {
        const val ACTION_SETTINGS_CHANGED = "com.example.aifloatingball.SETTINGS_CHANGED"
        const val EXTRA_LEFT_HANDED_MODE_CHANGED = "left_handed_mode_changed"
        const val PREF_LEFT_HANDED_MODE = "left_handed_mode"
        const val PREF_DEFAULT_SEARCH_ENGINE = "default_search_engine"
        const val PREF_DEFAULT_SEARCH_MODE = "default_search_mode"
        const val KEY_DEFAULT_SEARCH_MODE = "default_search_mode"
    }
} 