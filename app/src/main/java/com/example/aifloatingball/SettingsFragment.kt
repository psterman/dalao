package com.example.aifloatingball

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.*
import com.example.aifloatingball.service.FloatingWindowService
import com.example.aifloatingball.preference.SearchEnginePreference

class SettingsFragment : PreferenceFragmentCompat() {
    private lateinit var settingsManager: SettingsManager

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)
        settingsManager = SettingsManager.getInstance(requireContext())

        // 显示模式设置
        findPreference<ListPreference>("display_mode")?.apply {
            value = settingsManager.getDisplayMode()
            summary = if (value == "dynamic_island") "灵动岛" else "悬浮球"

            setOnPreferenceChangeListener { _, newValue ->
                val mode = newValue.toString()
                settingsManager.setDisplayMode(mode)
                summary = if (mode == "dynamic_island") "灵动岛" else "悬浮球"

                val serviceIntent = Intent(requireContext(), FloatingWindowService::class.java)
                if (mode == "dynamic_island") {
                    requireContext().stopService(serviceIntent)
                    Toast.makeText(requireContext(), "切换到灵动岛模式，悬浮球已关闭", Toast.LENGTH_SHORT).show()
                } else {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        requireContext().startForegroundService(serviceIntent)
                    } else {
                        requireContext().startService(serviceIntent)
                    }
                    Toast.makeText(requireContext(), "切换到悬浮球模式", Toast.LENGTH_SHORT).show()
                }
                true
            }
        }

        // 主题设置
        findPreference<ListPreference>("theme_mode")?.apply {
            value = settingsManager.getThemeMode().toString()
            setOnPreferenceChangeListener { _, newValue ->
                val mode = newValue.toString().toInt()
                settingsManager.setThemeMode(mode)
                true
            }
        }

        // 悬浮球透明度设置
        findPreference<SeekBarPreference>("ball_alpha")?.apply {
            value = settingsManager.getBallAlpha()
            setOnPreferenceChangeListener { _, newValue ->
                val alpha = newValue as Int
                settingsManager.setBallAlpha(alpha)
                val intent = Intent("com.example.aifloatingball.ACTION_UPDATE_ALPHA")
                intent.putExtra("alpha", alpha)
                requireContext().sendBroadcast(intent)
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

        // 剪贴板监听设置
        findPreference<SwitchPreferenceCompat>("clipboard_listener")?.apply {
            isChecked = settingsManager.isClipboardListenerEnabled()
            setOnPreferenceChangeListener { _, newValue ->
                settingsManager.setClipboardListenerEnabled(newValue as Boolean)
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

        // 自动粘贴设置
        findPreference<SwitchPreferenceCompat>("auto_paste")?.apply {
            isChecked = settingsManager.isAutoPasteEnabled()
            setOnPreferenceChangeListener { _, newValue ->
                settingsManager.setAutoPasteEnabled(newValue as Boolean)
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

        // 搜索引擎管理入口
        findPreference<Preference>("search_engine_manager")?.setOnPreferenceClickListener {
            startActivity(Intent(requireContext(), SearchEngineSettingsActivity::class.java))
            true
        }

        // AI搜索引擎管理入口
        findPreference<Preference>("ai_search_engine_manager")?.setOnPreferenceClickListener {
            startActivity(Intent(requireContext(), AISearchEngineSettingsActivity::class.java))
            true
        }

        // 多窗口搜索引擎设置
        setupWindowSearchEnginePreference("left_window_search_engine", 0)
        setupWindowSearchEnginePreference("center_window_search_engine", 1)
        setupWindowSearchEnginePreference("right_window_search_engine", 2)
    }

    private fun setupWindowSearchEnginePreference(key: String, position: Int) {
        findPreference<SearchEnginePreference>(key)?.let { pref ->
            pref.summary = (pref.summaryProvider as? SearchEnginePreference.SimpleSummaryProvider)?.provideSummary(pref)
            settingsManager.registerOnSettingChangeListener<String>(key) { _, _ ->
                pref.summary = (pref.summaryProvider as? SearchEnginePreference.SimpleSummaryProvider)?.provideSummary(pref)
            }
        }
    }
} 