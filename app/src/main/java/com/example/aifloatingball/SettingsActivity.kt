package com.example.aifloatingball

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.*

class SettingsActivity : AppCompatActivity() {
    
    private lateinit var settingsManager: SettingsManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
        settingsManager = SettingsManager.getInstance(this)
        
        // 设置标题栏
        supportActionBar?.apply {
            title = "设置"
            setDisplayHomeAsUpEnabled(true)
        }

        // 加载设置Fragment
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, SettingsFragment())
                .commit()
        }
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)

            // 获取SettingsManager实例
            val settingsManager = SettingsManager.getInstance(requireContext())

            // 主题设置
            findPreference<ListPreference>("theme_mode")?.apply {
                value = settingsManager.getThemeMode().toString()
                setOnPreferenceChangeListener { _, newValue ->
                    val mode = newValue.toString().toInt()
                    settingsManager.setThemeMode(mode)
                    AppCompatDelegate.setDefaultNightMode(mode)
                    activity?.recreate()
                    true
                }
            }

            // 默认页面设置
            findPreference<ListPreference>("default_page")?.apply {
                value = settingsManager.getDefaultPage()
                setOnPreferenceChangeListener { _, newValue ->
                    settingsManager.saveDefaultPage(newValue.toString())
                    true
                }
            }

            // 剪贴板监听设置
            findPreference<SwitchPreferenceCompat>("clipboard_listener")?.apply {
                isChecked = settingsManager.isClipboardListenerEnabled()
                setOnPreferenceChangeListener { _, newValue ->
                    settingsManager.setClipboardListenerEnabled(newValue as Boolean)
                    true
                }
            }

            // 左手模式设置
            findPreference<SwitchPreferenceCompat>("left_handed_mode")?.apply {
                isChecked = settingsManager.isLeftHandedMode()
                setOnPreferenceChangeListener { _, newValue ->
                    settingsManager.setLeftHandedMode(newValue as Boolean)
                    true
                }
            }

            // 默认搜索引擎设置
            findPreference<ListPreference>("default_search_engine")?.apply {
                value = settingsManager.getDefaultSearchEngine()
                setOnPreferenceChangeListener { _, newValue ->
                    settingsManager.setDefaultSearchEngine(newValue.toString())
                    true
                }
            }

            // 默认搜索模式设置
            findPreference<SwitchPreferenceCompat>("default_search_mode")?.apply {
                isChecked = settingsManager.isDefaultAIMode()
                setOnPreferenceChangeListener { _, newValue ->
                    settingsManager.setDefaultAIMode(newValue as Boolean)
                    true
                }
            }

            // 双窗口模式设置
            findPreference<SwitchPreferenceCompat>("use_dual_window_mode")?.apply {
                isChecked = settingsManager.getBoolean("use_dual_window_mode", true)
                setOnPreferenceChangeListener { _, newValue ->
                    settingsManager.putBoolean("use_dual_window_mode", newValue as Boolean)
                    true
                }
            }
            
            // 左侧窗口搜索引擎设置
            findPreference<ListPreference>("left_window_search_engine")?.apply {
                value = settingsManager.getLeftWindowSearchEngine()
                setOnPreferenceChangeListener { _, newValue ->
                    settingsManager.setLeftWindowSearchEngine(newValue.toString())
                    true
                }
            }
            
            // 右侧窗口搜索引擎设置
            findPreference<ListPreference>("right_window_search_engine")?.apply {
                value = settingsManager.getRightWindowSearchEngine()
                setOnPreferenceChangeListener { _, newValue ->
                    settingsManager.setRightWindowSearchEngine(newValue.toString())
                    true
                }
            }

            // 自动粘贴设置
            findPreference<SwitchPreferenceCompat>("auto_paste")?.apply {
                isChecked = settingsManager.isAutoPasteEnabled()
                setOnPreferenceChangeListener { _, newValue ->
                    settingsManager.setAutoPasteEnabled(newValue as Boolean)
                    true
                }
            }

            // 悬浮球大小设置
            findPreference<SeekBarPreference>("ball_size")?.apply {
                value = settingsManager.getBallSize()
                setOnPreferenceChangeListener { _, newValue ->
                    settingsManager.setBallSize(newValue as Int)
                    true
                }
            }

            // 隐私模式设置
            findPreference<SwitchPreferenceCompat>("privacy_mode")?.apply {
                isChecked = settingsManager.isPrivacyModeEnabled()
                setOnPreferenceChangeListener { _, newValue ->
                    settingsManager.setPrivacyModeEnabled(newValue as Boolean)
                    true
                }
            }

            // 菜单管理入口
            findPreference<Preference>("menu_manager")?.setOnPreferenceClickListener {
                startActivity(Intent(requireContext(), MenuManagerActivity::class.java))
                true
            }
        }
    }
} 