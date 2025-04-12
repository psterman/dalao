package com.example.aifloatingball

import android.content.Context
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

            // 默认窗口数量设置
            findPreference<ListPreference>("default_window_count")?.apply {
                value = settingsManager.getDefaultWindowCount().toString()
                setOnPreferenceChangeListener { _, newValue ->
                    settingsManager.setDefaultWindowCount(newValue.toString().toInt())
                    showApplyChangesDialog()
                    true
                }
            }
            
            // 左侧窗口搜索引擎设置
            findPreference<ListPreference>("left_window_search_engine")?.apply {
                value = settingsManager.getLeftWindowSearchEngine()
                setOnPreferenceChangeListener { _, newValue ->
                    settingsManager.setLeftWindowSearchEngine(newValue.toString())
                    showApplyChangesDialog()
                    true
                }
            }
            
            // 中间窗口搜索引擎设置
            findPreference<ListPreference>("center_window_search_engine")?.apply {
                value = settingsManager.getCenterWindowSearchEngine()
                setOnPreferenceChangeListener { _, newValue ->
                    settingsManager.setCenterWindowSearchEngine(newValue.toString())
                    showApplyChangesDialog()
                    true
                }
            }
            
            // 右侧窗口搜索引擎设置
            findPreference<ListPreference>("right_window_search_engine")?.apply {
                value = settingsManager.getRightWindowSearchEngine()
                setOnPreferenceChangeListener { _, newValue ->
                    settingsManager.setRightWindowSearchEngine(newValue.toString())
                    showApplyChangesDialog()
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

        /**
         * 显示应用更改对话框，询问用户是否要立即应用更改
         */
        private fun showApplyChangesDialog() {
            // 检查当前是否有浮动窗口服务在运行
            val serviceRunning = checkServiceRunning()
            
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
        
        /**
         * 重启浮动窗口服务以应用新设置
         */
        private fun restartFloatingService() {
            val context = requireContext()
            
            // 先停止当前服务
            context.stopService(Intent(context, DualFloatingWebViewService::class.java))
            
            // 稍微延迟后启动新服务
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                // 创建启动服务的Intent
                val intent = Intent(context, DualFloatingWebViewService::class.java)
                
                // 获取设置的窗口数量
                val windowCount = SettingsManager.getInstance(context).getDefaultWindowCount()
                intent.putExtra("window_count", windowCount)
                
                // 启动服务
                context.startService(intent)
            }, 500) // 500ms延迟确保服务完全停止
        }
    }
} 