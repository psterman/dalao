package com.example.aifloatingball

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.NotificationManagerCompat
import androidx.preference.*
import com.example.aifloatingball.service.FloatingWindowService
import com.example.aifloatingball.preference.SearchEnginePreference
import com.example.aifloatingball.service.DynamicIslandService
import com.example.aifloatingball.R
import com.example.aifloatingball.settings.AppSelectionActivity
import com.example.aifloatingball.service.DualFloatingWebViewService
import com.example.aifloatingball.preference.AlphaSliderDialogFragment

class SettingsFragment : PreferenceFragmentCompat() {
    private lateinit var settingsManager: SettingsManager

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)
        settingsManager = SettingsManager.getInstance(requireContext())

        // 初始化所有设置项
        setupGeneralPreferences()
        setupFloatingBallPreferences()
        setupDynamicIslandPreferences()
        setupBrowserAndSearchPreferences()
        setupAiAssistantPreferences()
        setupSearchEnginePreferences()

        // 初始化时根据当前设置，控制分类的可见性
        updateCategoryVisibility(settingsManager.getDisplayMode())
    }

    private fun setupGeneralPreferences() {
        // 显示模式
        findPreference<ListPreference>("display_mode")?.setOnPreferenceChangeListener { _, newValue ->
            val mode = newValue as String
                settingsManager.setDisplayMode(mode)
            updateCategoryVisibility(mode)
            Toast.makeText(requireContext(), "显示模式已切换，可能需要重启应用或返回主页后生效", Toast.LENGTH_LONG).show()
            true
        }

        // 主题
        findPreference<ListPreference>("theme_mode")?.setOnPreferenceChangeListener { _, newValue ->
            settingsManager.setThemeMode((newValue as String).toInt())
            activity?.recreate()
                true
            }

        // 剪贴板监听
        findPreference<SwitchPreferenceCompat>("clipboard_listener")?.setOnPreferenceChangeListener { _, newValue ->
            settingsManager.setClipboardListenerEnabled(newValue as Boolean)
            true
        }

        // 自动粘贴
        findPreference<SwitchPreferenceCompat>("auto_paste")?.setOnPreferenceChangeListener { _, newValue ->
            settingsManager.setAutoPasteEnabled(newValue as Boolean)
                true
            }

        findPreference<Preference>("view_search_history")?.setOnPreferenceClickListener {
            startActivity(Intent(requireContext(), SearchHistoryActivity::class.java))
            true
        }

        // 恢复默认设置
        findPreference<Preference>("restore_defaults")?.setOnPreferenceClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("确认恢复")
                .setMessage("您确定要将所有设置恢复为默认值吗？此操作无法撤销。")
                .setPositiveButton("恢复") { _, _ ->
                    settingsManager.clearAllSettings()
                    Toast.makeText(requireContext(), "已恢复默认设置，请重启应用", Toast.LENGTH_LONG).show()
                    // Recreate the activity to apply default settings
                    activity?.recreate()
                }
                .setNegativeButton("取消", null)
                .show()
            true
        }
    }

    private fun setupFloatingBallPreferences() {
        // 透明度
        findPreference<SeekBarPreference>("ball_alpha")?.setOnPreferenceChangeListener { _, newValue ->
                val alpha = newValue as Int
                settingsManager.setBallAlpha(alpha)
                val intent = Intent("com.example.aifloatingball.ACTION_UPDATE_ALPHA")
                intent.putExtra("alpha", alpha)
                requireContext().sendBroadcast(intent)
                true
            }

        // 自动贴边隐藏
        findPreference<SwitchPreferenceCompat>("auto_hide")?.setOnPreferenceChangeListener { _, newValue ->
            settingsManager.setAutoHide(newValue as Boolean)
            true
        }

        // 左手模式
        findPreference<SwitchPreferenceCompat>("left_handed_mode")?.setOnPreferenceChangeListener { _, newValue ->
                settingsManager.setLeftHandModeEnabled(newValue as Boolean)
                true
            }

        // 主菜单管理
        findPreference<Preference>("menu_manager")?.setOnPreferenceClickListener {
            startActivity(Intent(requireContext(), MenuManagerActivity::class.java))
            true
        }

        // 悬浮窗模式管理
        findPreference<Preference>("floating_window_mode_manager")?.setOnPreferenceClickListener {
            // TODO: 创建或链接到对应的 Activity
            Toast.makeText(requireContext(), "此功能尚未实现", Toast.LENGTH_SHORT).show()
                true
            }
        }

    private fun setupDynamicIslandPreferences() {
        // 启用通知监听
        val notificationPref = findPreference<SwitchPreferenceCompat>("enable_notification_listener")
        notificationPref?.setOnPreferenceChangeListener { _, newValue ->
            if (newValue as Boolean) {
                if (!isNotificationListenerEnabled()) {
                    // 跳转到系统设置页面让用户授权
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    // 返回 false，因为权限尚未授予，UI状态不应立即更新
                    return@setOnPreferenceChangeListener false
                }
            }
            true
        }

        // 通知应用管理
        findPreference<Preference>("select_apps_for_notification")?.setOnPreferenceClickListener {
            startActivity(Intent(requireContext(), AppSelectionActivity::class.java))
                true
            }
        }

    private fun setupBrowserAndSearchPreferences() {
        // 默认搜索模式
        findPreference<SwitchPreferenceCompat>("search_default_ai_mode")?.setOnPreferenceChangeListener { _, newValue ->
            settingsManager.setDefaultAIMode(newValue as Boolean)
                true
            }

        // 默认打开页面
        findPreference<ListPreference>("default_page")?.setOnPreferenceChangeListener { _, newValue ->
            settingsManager.saveDefaultPage(newValue as String)
            true
        }
        
        // 打开外部链接方式
        findPreference<ListPreference>("default_browser")?.setOnPreferenceChangeListener { _, newValue ->
            settingsManager.setDefaultBrowser(newValue as String)
            true
        }

        // 默认窗口数
        findPreference<ListPreference>("default_window_count")?.setOnPreferenceChangeListener { _, newValue ->
            settingsManager.setDefaultWindowCount((newValue as String).toInt())
            true
        }

        // 窗口1/2/3引擎 (SearchEnginePreference 会自动处理保存逻辑)

        // 恢复窗口位置
        findPreference<Preference>("reset_window_state")?.setOnPreferenceClickListener {
            val prefs = requireContext().getSharedPreferences(DualFloatingWebViewService.PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .remove(DualFloatingWebViewService.KEY_WINDOW_X)
                .remove(DualFloatingWebViewService.KEY_WINDOW_Y)
                .remove(DualFloatingWebViewService.KEY_WINDOW_WIDTH)
                .remove(DualFloatingWebViewService.KEY_WINDOW_HEIGHT)
                .apply()
            Toast.makeText(requireContext(), "窗口位置已重置", Toast.LENGTH_SHORT).show()
            true
        }
    }

    private fun setupAiAssistantPreferences() {
        // AI 指令中心
        findPreference<Preference>("master_prompt_settings")?.setOnPreferenceClickListener {
            startActivity(Intent(requireContext(), MasterPromptSettingsActivity::class.java))
            true
        }

        // API 设置
        findPreference<Preference>("ai_api_settings")?.setOnPreferenceClickListener {
            startActivity(Intent(requireContext(), AIApiSettingsActivity::class.java))
            true
        }
    }

    private fun setupSearchEnginePreferences() {
        // The navigation is now handled by the OnPreferenceStartFragmentCallback in SettingsActivity
        // so the click listener for web_search_engine_selection is no longer needed here.

        findPreference<Preference>("web_search_engine_manager")?.setOnPreferenceClickListener {
            startActivity(Intent(requireContext(), SearchEngineGroupManagerActivity::class.java))
            true
        }

        findPreference<Preference>("ai_search_engine_manager")?.setOnPreferenceClickListener {
            startActivity(Intent(requireContext(), AISearchEngineSettingsActivity::class.java))
            true
        }
    }

    private fun updateCategoryVisibility(mode: String) {
        findPreference<PreferenceCategory>("category_floating_ball")?.isVisible = (mode == "floating_ball")
        findPreference<PreferenceCategory>("category_dynamic_island")?.isVisible = (mode == "dynamic_island")
    }

    override fun onResume() {
        super.onResume()
        // 更新通知监听开关的状态
        findPreference<SwitchPreferenceCompat>("enable_notification_listener")?.isChecked = isNotificationListenerEnabled()
    }

    private fun isNotificationListenerEnabled(): Boolean {
        return NotificationManagerCompat.getEnabledListenerPackages(requireContext())
            .contains(requireContext().packageName)
    }
} 