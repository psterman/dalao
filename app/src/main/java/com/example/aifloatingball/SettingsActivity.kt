package com.example.aifloatingball

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
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

            // 设置各个选项的初始值
            findPreference<ListPreference>("default_page")?.apply {
                value = settingsManager.getDefaultPage()
                setOnPreferenceChangeListener { _, newValue ->
                    settingsManager.saveDefaultPage(newValue.toString())
                    true
                }
            }

            findPreference<SwitchPreferenceCompat>("clipboard_listener")?.apply {
                isChecked = settingsManager.isClipboardListenerEnabled()
                setOnPreferenceChangeListener { _, newValue ->
                    settingsManager.setClipboardListenerEnabled(newValue as Boolean)
                    true
                }
            }

            findPreference<SwitchPreferenceCompat>("left_handed_mode")?.apply {
                isChecked = settingsManager.isLeftHandedMode()
                setOnPreferenceChangeListener { _, newValue ->
                    settingsManager.setLeftHandedMode(newValue as Boolean)
                    true
                }
            }

            findPreference<ListPreference>("default_search_engine")?.apply {
                value = settingsManager.getDefaultSearchEngine()
                setOnPreferenceChangeListener { _, newValue ->
                    settingsManager.setDefaultSearchEngine(newValue.toString())
                    true
                }
            }

            findPreference<SwitchPreferenceCompat>("default_search_mode")?.apply {
                isChecked = settingsManager.isDefaultAIMode()
                setOnPreferenceChangeListener { _, newValue ->
                    settingsManager.setDefaultAIMode(newValue as Boolean)
                    true
                }
            }

            findPreference<SwitchPreferenceCompat>("auto_paste")?.apply {
                isChecked = settingsManager.isAutoPasteEnabled()
                setOnPreferenceChangeListener { _, newValue ->
                    settingsManager.setAutoPasteEnabled(newValue as Boolean)
                    true
                }
            }

            findPreference<SeekBarPreference>("ball_size")?.apply {
                value = settingsManager.getBallSize()
                setOnPreferenceChangeListener { _, newValue ->
                    settingsManager.setBallSize(newValue as Int)
                    true
                }
            }

            findPreference<ListPreference>("layout_theme")?.apply {
                value = settingsManager.getLayoutTheme().toString()
                setOnPreferenceChangeListener { _, newValue ->
                    settingsManager.saveLayoutTheme(newValue.toString().toInt())
                    true
                }
            }

            findPreference<SwitchPreferenceCompat>("dark_mode")?.apply {
                isChecked = settingsManager.getDarkMode() == 2
                setOnPreferenceChangeListener { _, newValue ->
                    settingsManager.setDarkMode(if (newValue as Boolean) 2 else 1)
                    true
                }
            }

            findPreference<SwitchPreferenceCompat>("privacy_mode")?.apply {
                isChecked = settingsManager.isPrivacyModeEnabled()
                setOnPreferenceChangeListener { _, newValue ->
                    settingsManager.setPrivacyModeEnabled(newValue as Boolean)
                    true
                }
            }

            // 添加菜单管理入口
            findPreference<Preference>("menu_manager")?.setOnPreferenceClickListener {
                startActivity(Intent(requireContext(), MenuManagerActivity::class.java))
                true
            }
        }
    }
} 