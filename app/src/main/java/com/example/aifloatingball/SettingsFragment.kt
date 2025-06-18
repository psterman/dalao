package com.example.aifloatingball

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.*
import com.example.aifloatingball.service.FloatingWindowService
import com.example.aifloatingball.preference.SearchEnginePreference
import com.example.aifloatingball.service.DynamicIslandService
import com.example.aifloatingball.R

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
                Toast.makeText(requireContext(), "显示模式已切换，重启应用或返回主页后生效", Toast.LENGTH_LONG).show()
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
            isChecked = settingsManager.isLeftHandModeEnabled()
            setOnPreferenceChangeListener { _, newValue ->
                settingsManager.setLeftHandModeEnabled(newValue as Boolean)
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

        // AI 指令中心入口
        findPreference<Preference>("master_prompt_settings")?.setOnPreferenceClickListener {
            startActivity(Intent(requireContext(), MasterPromptSettingsActivity::class.java))
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