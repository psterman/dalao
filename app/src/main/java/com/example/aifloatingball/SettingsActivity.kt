package com.example.aifloatingball

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.*

class SettingsActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, SettingsFragment())
                .commit()
        }
        
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        }
        onBackPressedDispatcher.addCallback(this, callback)
        
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
} 