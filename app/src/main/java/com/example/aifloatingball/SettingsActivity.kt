package com.example.aifloatingball

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.*
import com.example.aifloatingball.utils.ServiceUtils
import com.example.aifloatingball.preference.SearchEnginePreference
import com.example.aifloatingball.service.DualFloatingWebViewService
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings
import android.net.Uri
import android.util.Log
import com.example.aifloatingball.service.FloatingWindowService
import com.example.aifloatingball.service.DynamicIslandService
import android.widget.Toast
import android.text.TextUtils
import androidx.core.app.NotificationManagerCompat
import com.example.aifloatingball.service.NotificationListener

class SettingsActivity : AppCompatActivity() {
    
    private lateinit var settingsManager: SettingsManager
    private val preferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "display_mode") {
            Log.d("SettingsActivity", "Display mode changed, updating services.")
            updateDisplayMode()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsManager = SettingsManager.getInstance(this)
        settingsManager.registerOnSharedPreferenceChangeListener(preferenceChangeListener)

        setContentView(R.layout.settings_activity)
        
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

    override fun onDestroy() {
        super.onDestroy()
        settingsManager.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    private fun updateDisplayMode() {
        val displayMode = settingsManager.getDisplayMode()
        Log.d("SettingsActivity", "Updating display mode to: $displayMode")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "需要'显示在其他应用上层'的权限才能切换模式", Toast.LENGTH_LONG).show()
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName"))
            startActivity(intent)
            return
        }

        // 停止所有相关服务
        stopService(Intent(this, FloatingWindowService::class.java))
        stopService(Intent(this, DynamicIslandService::class.java))

        // 根据新模式启动正确的服务
        when (displayMode) {
            "floating_ball" -> {
                startService(Intent(this, FloatingWindowService::class.java))
            }
            "dynamic_island" -> {
                startService(Intent(this, DynamicIslandService::class.java))
            }
        }
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        private lateinit var settingsManager: SettingsManager

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)
            settingsManager = SettingsManager.getInstance(requireContext())

            // 显示模式设置
            findPreference<ListPreference>("display_mode")?.apply {
                value = settingsManager.getDisplayMode()
                setOnPreferenceChangeListener { _, newValue ->
                    settingsManager.setDisplayMode(newValue as String)
                    true
                }
            }

            // 启用灵动岛通知
            val notificationPref = findPreference<SwitchPreferenceCompat>("enable_notification_listener")
            notificationPref?.setOnPreferenceChangeListener { _, newValue ->
                val isEnabled = newValue as Boolean
                if (isEnabled) {
                    if (!isNotificationListenerEnabled()) {
                        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        // Don't update the switch until the user has actually granted the permission.
                        return@setOnPreferenceChangeListener false
                    }
                }
                true
            }

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
                isChecked = settingsManager.isLeftHandModeEnabled()
                setOnPreferenceChangeListener { _, newValue ->
                    settingsManager.setLeftHandModeEnabled(newValue as Boolean)
                    true
                }
            }

            // 默认搜索引擎设置
            findPreference<SearchEnginePreference>("default_search_engine")?.apply {
                value = settingsManager.getDefaultSearchEngine()
                setOnPreferenceChangeListener { _, newValue ->
                    settingsManager.setDefaultSearchEngine(newValue.toString())
                    true
                }
            }

            // 默认搜索模式设置
            findPreference<SwitchPreferenceCompat>("search_default_ai_mode")?.apply {
                isChecked = settingsManager.isDefaultAIMode()
                setOnPreferenceChangeListener { _, newValue ->
                    settingsManager.setDefaultAIMode(newValue as Boolean)
                    true
                }
            }

            // AI设置中的默认搜索模式
            findPreference<SwitchPreferenceCompat>("ai_default_search_mode")?.apply {
                isChecked = settingsManager.isDefaultAIMode()
                setOnPreferenceChangeListener { _, newValue ->
                    settingsManager.setDefaultAIMode(newValue as Boolean)
                    true
                }
            }

            // 默认浏览器设置
            findPreference<ListPreference>("default_browser")?.apply {
                value = settingsManager.getDefaultBrowser()
                setOnPreferenceChangeListener { _, newValue ->
                    settingsManager.setDefaultBrowser(newValue as String)
                    true
                }
            }

            // 默认窗口数量设置
            findPreference<ListPreference>("default_window_count")?.apply {
                value = settingsManager.getDefaultWindowCount().toString()
                setOnPreferenceChangeListener { _, newValue ->
                    settingsManager.setDefaultWindowCount((newValue as String).toInt())
                    ServiceUtils.restartFloatingService(requireContext())
                    true
                }
            }
            
            // 左侧窗口搜索引擎设置
            findPreference<SearchEnginePreference>("left_window_search_engine")?.apply {
                value = settingsManager.getLeftWindowSearchEngine()
                setOnPreferenceChangeListener { _, newValue ->
                    settingsManager.setLeftWindowSearchEngine(newValue.toString())
                    true
                }
            }
            
            // 中间窗口搜索引擎设置
            findPreference<SearchEnginePreference>("center_window_search_engine")?.apply {
                value = settingsManager.getCenterWindowSearchEngine()
                setOnPreferenceChangeListener { _, newValue ->
                    settingsManager.setCenterWindowSearchEngine(newValue.toString())
                    true
                }
            }
            
            // 右侧窗口搜索引擎设置
            findPreference<SearchEnginePreference>("right_window_search_engine")?.apply {
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

            // 隐私模式设置
            findPreference<SwitchPreferenceCompat>("privacy_mode")?.apply {
                isChecked = settingsManager.isPrivacyModeEnabled()
                setOnPreferenceChangeListener { _, newValue ->
                    settingsManager.setPrivacyModeEnabled(newValue as Boolean)
                    true
                }
            }

            // 搜索引擎分类显示设置
            findPreference<SwitchPreferenceCompat>("show_ai_engine_category")?.apply {
                isChecked = settingsManager.showAIEngineCategory()
                setOnPreferenceChangeListener { _, newValue ->
                    settingsManager.setShowAIEngineCategory(newValue as Boolean)
                    true
                }
            }

            // 搜索引擎管理入口
            findPreference<Preference>("search_engine_manager")?.apply {
                setOnPreferenceClickListener {
                    try {
                startActivity(Intent(requireContext(), SearchEngineSettingsActivity::class.java))
                true
                    } catch (e: Exception) {
                        e.printStackTrace()
                        false
                    }
                }
            }
            
            // AI搜索引擎管理入口（移除重复的ai_engine_settings）
            findPreference<Preference>("ai_search_engine_manager")?.apply {
                setOnPreferenceClickListener {
                    try {
                startActivity(Intent(requireContext(), AISearchEngineSettingsActivity::class.java))
                true
                    } catch (e: Exception) {
                        e.printStackTrace()
                        false
                    }
                }
            }
            
            // 应用搜索设置入口
            findPreference<Preference>("app_search_settings")?.apply {
                setOnPreferenceClickListener {
                    try {
                startActivity(Intent(requireContext(), AppSearchSettingsActivity::class.java))
                true
                    } catch (e: Exception) {
                        e.printStackTrace()
                        false
                    }
                }
            }
            
            // 搜索引擎组合管理入口
            findPreference<Preference>("search_engine_group_manager")?.apply {
                setOnPreferenceClickListener {
                    try {
                startActivity(Intent(requireContext(), SearchEngineGroupManagerActivity::class.java))
                true
                    } catch (e: Exception) {
                        e.printStackTrace()
                        false
                    }
                }
            }

            // 菜单管理入口
            findPreference<Preference>("menu_manager")?.apply {
                setOnPreferenceClickListener {
                    try {
                        startActivity(Intent(requireContext(), MenuManagerActivity::class.java))
                        true
                    } catch (e: Exception) {
                        e.printStackTrace()
                        false
                    }
                }
            }

            // AI API设置入口
            findPreference<Preference>("ai_api_settings")?.apply {
                setOnPreferenceClickListener {
                    try {
                startActivity(Intent(requireContext(), AIApiSettingsActivity::class.java))
                true
                    } catch (e: Exception) {
                        e.printStackTrace()
                        false
                    }
                }
            }
            
            // 恢复窗口原始状态
            findPreference<Preference>("reset_window_state")?.setOnPreferenceClickListener {
                val editor = settingsManager.getSharedPreferences().edit()
                editor.remove(DualFloatingWebViewService.KEY_WINDOW_X)
                editor.remove(DualFloatingWebViewService.KEY_WINDOW_Y)
                editor.remove(DualFloatingWebViewService.KEY_WINDOW_WIDTH)
                editor.remove(DualFloatingWebViewService.KEY_WINDOW_HEIGHT)
                editor.apply()
                
                true
            }
        }

        override fun onResume() {
            super.onResume()
            // Update the switch state based on whether the permission is currently granted.
            val notificationPref = findPreference<SwitchPreferenceCompat>("enable_notification_listener")
            notificationPref?.isChecked = isNotificationListenerEnabled()
        }

        private fun isNotificationListenerEnabled(): Boolean {
            val enabledListeners = NotificationManagerCompat.getEnabledListenerPackages(requireContext())
            return enabledListeners.contains(requireContext().packageName)
        }

        /**
         * 显示应用更改对话框，询问用户是否要立即应用更改
         */
        private fun showRestartServiceDialog() {
            // 检查当前是否有浮动窗口服务在运行
            val serviceRunning = ServiceUtils.isServiceRunning(requireContext(), DualFloatingWebViewService::class.java.name)
            
            if (!serviceRunning) {
                return  // 如果服务没有运行，无需显示对话框
            }
            
            // 创建对话框
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("应用更改")
                .setMessage("窗口设置已更改，是否立即应用到当前运行的浮动窗口？")
                .setPositiveButton("应用") { _, _ ->
                    restartFloatingService()
                }
                .setNegativeButton("稍后") { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
        }
        
        /**
         * 重启浮动窗口服务，应用新的设置
         */
        private fun restartFloatingService() {
            val context = requireContext()
            
            // 停止现有服务
            context.stopService(Intent(context, DualFloatingWebViewService::class.java))
            
            // 等待短暂时间确保服务已停止
            android.os.Handler().postDelayed({
                // 启动新服务，并传递窗口数量
                val intent = Intent(context, DualFloatingWebViewService::class.java).apply {
                    putExtra("window_count", settingsManager.getDefaultWindowCount())
                }
                context.startService(intent)
            }, 500) // 500毫秒延迟
        }
        
        /**
         * 检查DualFloatingWebViewService是否正在运行
         */
        private fun checkServiceRunning(): Boolean {
            val manager = requireContext().getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
                if (DualFloatingWebViewService::class.java.name == service.service.className) {
                    return true
                }
            }
            return false
        }
    }
} 