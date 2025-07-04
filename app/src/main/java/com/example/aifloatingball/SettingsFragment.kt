package com.example.aifloatingball

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
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
            val builder = AlertDialog.Builder(requireContext())
                .setTitle("确认恢复")
                .setMessage("您确定要将所有设置恢复为默认值吗？此操作无法撤销。")

            val positiveAction = DialogInterface.OnClickListener { _, _ ->
                    settingsManager.clearAllSettings()
                    Toast.makeText(requireContext(), "已恢复默认设置，请重启应用", Toast.LENGTH_LONG).show()
                    activity?.recreate()
                }
            val negativeAction = DialogInterface.OnClickListener { _, _ ->
                // User cancelled the dialog
            }

            if (settingsManager.isLeftHandedModeEnabled()) {
                // 左手模式: "恢复"在左，"取消"在右
                builder.setPositiveButton("取消", negativeAction)
                builder.setNegativeButton("恢复", positiveAction)
            } else {
                // 右手模式（默认）: "恢复"在右，"取消"在左
                builder.setPositiveButton("恢复", positiveAction)
                builder.setNegativeButton("取消", negativeAction)
            }
            
            builder.show()
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
            Log.d("SettingsFragment", "=== 开始恢复窗口位置操作 ===")
            
            val prefs = requireContext().getSharedPreferences(DualFloatingWebViewService.PREFS_NAME, Context.MODE_PRIVATE)
            
            // 记录重置前的值
            val beforeX = prefs.getInt(DualFloatingWebViewService.KEY_WINDOW_X, -1)
            val beforeY = prefs.getInt(DualFloatingWebViewService.KEY_WINDOW_Y, -1)
            val beforeWidth = prefs.getInt(DualFloatingWebViewService.KEY_WINDOW_WIDTH, -1)
            val beforeHeight = prefs.getInt(DualFloatingWebViewService.KEY_WINDOW_HEIGHT, -1)
            val beforeCount = prefs.getInt(DualFloatingWebViewService.KEY_WINDOW_COUNT, -1)
            
            Log.d("SettingsFragment", "重置前窗口状态: x=$beforeX, y=$beforeY, width=$beforeWidth, height=$beforeHeight, count=$beforeCount")
            
            // 检查DualFloatingWebViewService是否正在运行
            val isServiceRunning = DualFloatingWebViewService.isRunning
            Log.d("SettingsFragment", "DualFloatingWebViewService运行状态: $isServiceRunning")
            
            if (!isServiceRunning) {
                Log.w("SettingsFragment", "服务未运行，仅清除SharedPreferences")
                Toast.makeText(requireContext(), "服务未运行，请先启动多窗口搜索", Toast.LENGTH_LONG).show()
                return@setOnPreferenceClickListener true
            }
            
            // 清除SharedPreferences
            val editor = prefs.edit()
            editor.remove(DualFloatingWebViewService.KEY_WINDOW_X)
            editor.remove(DualFloatingWebViewService.KEY_WINDOW_Y)
            editor.remove(DualFloatingWebViewService.KEY_WINDOW_WIDTH)
            editor.remove(DualFloatingWebViewService.KEY_WINDOW_HEIGHT)
            editor.remove(DualFloatingWebViewService.KEY_WINDOW_COUNT) // 也重置窗口数量
            val success = editor.commit() // 使用commit而不是apply以确保立即保存
            
            Log.d("SettingsFragment", "SharedPreferences清除${if (success) "成功" else "失败"}")
            
            // 验证清除结果
            val afterX = prefs.getInt(DualFloatingWebViewService.KEY_WINDOW_X, -1)
            val afterY = prefs.getInt(DualFloatingWebViewService.KEY_WINDOW_Y, -1)
            val afterWidth = prefs.getInt(DualFloatingWebViewService.KEY_WINDOW_WIDTH, -1)
            val afterHeight = prefs.getInt(DualFloatingWebViewService.KEY_WINDOW_HEIGHT, -1)
            val afterCount = prefs.getInt(DualFloatingWebViewService.KEY_WINDOW_COUNT, -1)
            
            Log.d("SettingsFragment", "重置后窗口状态: x=$afterX, y=$afterY, width=$afterWidth, height=$afterHeight, count=$afterCount")
            
            // 发送广播通知DualFloatingWebViewService重置窗口位置和状态
            val intent = Intent("com.example.aifloatingball.ACTION_RESET_WINDOW_STATE")
            Log.d("SettingsFragment", "准备发送广播: ${intent.action}")
            
            try {
                requireContext().sendBroadcast(intent)
                Log.d("SettingsFragment", "✓ 重置窗口状态广播发送成功")
                
                // 给一点时间让服务处理广播
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    Log.d("SettingsFragment", "延迟检查重置结果...")
                    Toast.makeText(requireContext(), "窗口已恢复到初始状态 (90%x60%，居中显示)", Toast.LENGTH_LONG).show()
                }, 500)
                
            } catch (e: Exception) {
                Log.e("SettingsFragment", "发送重置广播失败", e)
                Toast.makeText(requireContext(), "重置失败: ${e.message}", Toast.LENGTH_LONG).show()
            }
            
            Log.d("SettingsFragment", "=== 恢复窗口位置操作完成 ===")
            true
        }
    }

    private fun setupAiAssistantPreferences() {
        // 默认AI搜索模式
        findPreference<SwitchPreferenceCompat>("search_default_ai_mode")?.setOnPreferenceChangeListener { _, newValue ->
            settingsManager.setDefaultAIMode(newValue as Boolean)
            true
        }

        // AI 指令中心
        findPreference<Preference>("master_prompt_settings")?.setOnPreferenceClickListener {
            startActivity(Intent(requireContext(), MasterPromptSimpleActivity::class.java))
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
        findPreference<PreferenceCategory>("category_simple_mode")?.isVisible = (mode == "simple_mode")
        // AI助手分类在所有模式下都可见
        findPreference<PreferenceCategory>("category_ai_assistant")?.isVisible = true
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