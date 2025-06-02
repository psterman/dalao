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

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

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
            findPreference<SearchEnginePreference>("default_search_engine")?.apply {
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
                    ServiceUtils.restartFloatingService(requireContext())
                    true
                }
            }
            
            // 中间窗口搜索引擎设置
            findPreference<SearchEnginePreference>("center_window_search_engine")?.apply {
                value = settingsManager.getCenterWindowSearchEngine()
                setOnPreferenceChangeListener { _, newValue ->
                    settingsManager.setCenterWindowSearchEngine(newValue.toString())
                    ServiceUtils.restartFloatingService(requireContext())
                    true
                }
            }
            
            // 右侧窗口搜索引擎设置
            findPreference<SearchEnginePreference>("right_window_search_engine")?.apply {
                value = settingsManager.getRightWindowSearchEngine()
                setOnPreferenceChangeListener { _, newValue ->
                    settingsManager.setRightWindowSearchEngine(newValue.toString())
                    ServiceUtils.restartFloatingService(requireContext())
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
            
            // 应用搜索设置入口
            findPreference<Preference>("app_search_settings")?.setOnPreferenceClickListener {
                startActivity(Intent(requireContext(), AppSearchSettingsActivity::class.java))
                true
            }
            
            // 搜索引擎组合管理入口
            findPreference<Preference>("search_engine_group_manager")?.setOnPreferenceClickListener {
                startActivity(Intent(requireContext(), SearchEngineGroupManagerActivity::class.java))
                true
            }

            // AI API设置入口
            findPreference<Preference>("ai_api_settings")?.setOnPreferenceClickListener {
                startActivity(Intent(requireContext(), AIApiSettingsActivity::class.java))
                true
            }
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