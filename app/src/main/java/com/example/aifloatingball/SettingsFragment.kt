package com.example.aifloatingball

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.*
import com.example.aifloatingball.utils.ServiceUtils
import com.example.aifloatingball.preference.SearchEnginePreference

class SettingsFragment : PreferenceFragmentCompat() {
    private lateinit var settingsManager: SettingsManager

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)
        settingsManager = SettingsManager.getInstance(requireContext())

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

        // 悬浮球透明度设置
        findPreference<SeekBarPreference>("ball_alpha")?.apply {
            value = settingsManager.getBallAlpha()
            setOnPreferenceChangeListener { _, newValue ->
                settingsManager.setBallAlpha(newValue as Int)
                // 立即更新悬浮球透明度，不需要重启服务
                val intent = Intent("com.example.aifloatingball.ACTION_UPDATE_ALPHA")
                intent.putExtra("alpha", newValue as Int)
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
        val preference = findPreference<SearchEnginePreference>(key)
        preference?.let { pref ->
            // 确保初始摘要是正确的
            pref.summary = (pref.summaryProvider as SearchEnginePreference.SimpleSummaryProvider).provideSummary(pref)

            // 注册监听器以实时更新摘要
            settingsManager.registerOnSettingChangeListener<String>(key) { _, newValue ->
                pref.summary = (pref.summaryProvider as SearchEnginePreference.SimpleSummaryProvider).provideSummary(pref)
            }
        }
    }
} 