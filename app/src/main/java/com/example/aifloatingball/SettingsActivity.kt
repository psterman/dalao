package com.example.aifloatingball

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Switch
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
            findPreference<SwitchPreferenceCompat>(PREF_LEFT_HANDED_MODE)?.setOnPreferenceChangeListener { _, newValue ->
                val intent = Intent(ACTION_SETTINGS_CHANGED).apply {
                    putExtra(EXTRA_LEFT_HANDED_MODE_CHANGED, true)
                }
                requireContext().sendBroadcast(intent)
                true
            }

            // 默认搜索模式
            findPreference<SwitchPreferenceCompat>(PREF_DEFAULT_SEARCH_MODE)?.setOnPreferenceChangeListener { _, newValue ->
                updateDefaultSearchEngineList(newValue as Boolean)
                true
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
            val isAIMode = findPreference<SwitchPreferenceCompat>(PREF_DEFAULT_SEARCH_MODE)?.isChecked ?: true
            updateDefaultSearchEngineList(isAIMode)
        }

        private fun updateDefaultSearchEngineList(isAIMode: Boolean) {
            val defaultSearchPref = findPreference<ListPreference>(PREF_DEFAULT_SEARCH_ENGINE)
            val engines = if (isAIMode) {
                AISearchEngine.DEFAULT_AI_ENGINES
            } else {
                SearchActivity.NORMAL_SEARCH_ENGINES
            }

            val engineNames = engines.map { it.name }.toTypedArray()
            val engineValues = engines.map { "${it.name}|${it.url}" }.toTypedArray()

            defaultSearchPref?.apply {
                entries = engineNames
                entryValues = engineValues
                if (value == null || !engineValues.contains(value)) {
                    value = engineValues.firstOrNull()
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
    }
} 