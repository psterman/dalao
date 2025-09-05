package com.example.aifloatingball

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.adapter.SettingsSearchResultAdapter
import com.example.aifloatingball.model.SearchableSetting
import com.example.aifloatingball.model.SearchEngine
import com.example.aifloatingball.service.DynamicIslandService
import com.example.aifloatingball.service.FloatingWindowService
import com.example.aifloatingball.service.SimpleModeService
import androidx.preference.SwitchPreferenceCompat
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import android.content.DialogInterface
import androidx.preference.ListPreference
import android.content.Context
import com.example.aifloatingball.ui.onboarding.OnboardingActivity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager

class SettingsActivity : AppCompatActivity(), PreferenceFragmentCompat.OnPreferenceStartFragmentCallback, SearchView.OnQueryTextListener, SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var searchManager: SettingsSearchManager
    private lateinit var resultsAdapter: SettingsSearchResultAdapter
    private lateinit var resultsRecyclerView: RecyclerView
    private lateinit var settingsContainer: View
    private lateinit var settingsManager: SettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        settingsManager = SettingsManager.getInstance(this)

        // 应用适老化主题
        applyElderlyTheme()

        setContentView(R.layout.activity_settings)

        settingsManager.registerOnSharedPreferenceChangeListener(this)
        applyLayoutDirection()


        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings_container, SettingsFragment())
                .commit()
        }

        // 设置适老化的ActionBar
        setupElderlyActionBar()

        // Initialize search components
        searchManager = SettingsSearchManager(this)
        settingsContainer = findViewById(R.id.settings_container)
        resultsRecyclerView = findViewById(R.id.search_results_recycler_view)
        setupRecyclerView()
    }

    override fun onResume() {
        super.onResume()
        updateDisplayMode()
        // 确保主题正确应用
        applyElderlyTheme()
    }

    private fun applyElderlyTheme() {
        try {
            // 设置状态栏和导航栏颜色为适老化蓝色
            window.statusBarColor = androidx.core.content.ContextCompat.getColor(this, R.color.elderly_primary)
            window.navigationBarColor = androidx.core.content.ContextCompat.getColor(this, R.color.elderly_background)

            // 设置背景色
            findViewById<View>(android.R.id.content)?.setBackgroundColor(
                androidx.core.content.ContextCompat.getColor(this, R.color.elderly_background)
            )
        } catch (e: Exception) {
            android.util.Log.e("SettingsActivity", "Error applying elderly theme", e)
        }
    }

    private fun setupElderlyActionBar() {
        try {
            supportActionBar?.let { actionBar ->
                actionBar.setDisplayHomeAsUpEnabled(true)
                actionBar.title = "设置"

                // 设置ActionBar背景色为适老化蓝色
                actionBar.setBackgroundDrawable(
                    android.graphics.drawable.ColorDrawable(
                        androidx.core.content.ContextCompat.getColor(this, R.color.elderly_primary)
                    )
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("SettingsActivity", "Error setting up elderly action bar", e)
        }
    }

    private fun updateDisplayMode() {
        // This function is crucial for starting/stopping services based on settings.
        val intentBall = Intent(this, FloatingWindowService::class.java)
        val intentIsland = Intent(this, DynamicIslandService::class.java)
        val intentSimple = Intent(this, SimpleModeService::class.java)
        stopService(intentBall)
        stopService(intentIsland)
        stopService(intentSimple)

        when (settingsManager.getDisplayMode()) {
            "floating_ball" -> startService(intentBall)
            "dynamic_island" -> startService(intentIsland)
            "simple_mode" -> startActivity(Intent(this, SimpleModeActivity::class.java))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::settingsManager.isInitialized) {
            settingsManager.unregisterOnSharedPreferenceChangeListener(this)
        }
    }

    private fun setupRecyclerView() {
        resultsAdapter = SettingsSearchResultAdapter(emptyList()) { setting ->
            // Hide search view
            val menu = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)?.menu
            menu?.findItem(R.id.action_search)?.collapseActionView()

            // Find the preference in the root fragment and perform a click
            val settingsFragment = supportFragmentManager.findFragmentById(R.id.settings_container) as? SettingsFragment
            settingsFragment?.findPreference<Preference>(setting.key)?.performClick()
            }
        resultsRecyclerView.layoutManager = LinearLayoutManager(this)
        resultsRecyclerView.adapter = resultsAdapter
    }

    override fun onPreferenceStartFragment(caller: PreferenceFragmentCompat, pref: Preference): Boolean {
        // Instantiate the new Fragment
        val args = pref.extras
        val fragment = supportFragmentManager.fragmentFactory.instantiate(
            classLoader,
            pref.fragment!!
        ).apply {
            arguments = args
            setTargetFragment(caller, 0)
        }
        // Replace the existing Fragment with the new Fragment
        supportFragmentManager.beginTransaction()
            .replace(R.id.settings_container, fragment)
            .addToBackStack(null)
            .commit()
        supportActionBar?.title = pref.title
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.search_history_menu, menu) // Re-using the same menu
        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as SearchView
        searchView.setOnQueryTextListener(this)
        searchView.queryHint = "搜索设置项..."

        searchItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                // Show search results, hide settings
                resultsRecyclerView.visibility = View.VISIBLE
                settingsContainer.visibility = View.GONE
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                // Hide search results, show settings
                resultsRecyclerView.visibility = View.GONE
                settingsContainer.visibility = View.VISIBLE
                return true
            }
        })
        return true
    }

    override fun onQueryTextSubmit(query: String?): Boolean {
        return false // Let the list filter handle it
    }

    override fun onQueryTextChange(newText: String?): Boolean {
        val results = searchManager.search(newText.orEmpty())
        resultsAdapter.updateData(results)
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        if (supportFragmentManager.popBackStackImmediate()) {
            supportActionBar?.title = "设置" // Restore title on back press
            return true
        }
        onBackPressed()
        return true
    }

    private fun applyLayoutDirection() {
        if (settingsManager.isLeftHandedModeEnabled()) {
            window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
        } else {
            window.decorView.layoutDirection = View.LAYOUT_DIRECTION_LTR
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        when (key) {
            "left_handed_mode" -> {
                applyLayoutDirection()
                
                // Link floating ball position to left-handed mode
                val isLeftHanded = settingsManager.isLeftHandedModeEnabled()
                val currentPosition = settingsManager.getFloatingBallPosition()
                val screenWidth = resources.displayMetrics.widthPixels
                
                val newX = if (isLeftHanded) 0 else screenWidth
                
                settingsManager.setFloatingBallPosition(newX, currentPosition.second)
                
                // Notify the service to update the ball's position
                val intent = Intent(FloatingWindowService.ACTION_UPDATE_POSITION)
                sendBroadcast(intent)
            }
            "display_mode" -> {
                updateDisplayMode()
            }
        }
    }

    abstract class BaseSettingsFragment : PreferenceFragmentCompat() {
        // No longer needed, as the Activity handles the layout direction globally.
    }

    class SettingsFragment : BaseSettingsFragment() {
        private lateinit var settingsManager: SettingsManager

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            settingsManager = SettingsManager.getInstance(requireContext())
            setPreferencesFromResource(R.xml.root_preferences, rootKey)

            updateCategoryVisibility(settingsManager.getDisplayMode())

            findPreference<ListPreference>("display_mode")?.setOnPreferenceChangeListener { _, newValue ->
                updateCategoryVisibility(newValue as String)
                true
            }

            findPreference<Preference>("view_search_history")?.setOnPreferenceClickListener {
                val intent = Intent(activity, SearchHistoryActivity::class.java)
                startActivity(intent)
                true
            }

            // 权限管理
            findPreference<Preference>("permission_management")?.setOnPreferenceClickListener {
                startActivity(Intent(requireContext(), PermissionManagementActivity::class.java))
                true
            }

            // 新手入门指南
            findPreference<Preference>("onboarding_guide")?.setOnPreferenceClickListener {
                startActivity(Intent(requireContext(), OnboardingActivity::class.java))
                true
            }

            // 主题切换
            findPreference<ListPreference>("theme_mode")?.setOnPreferenceChangeListener { _, newValue ->
                settingsManager.setThemeMode((newValue as String).toInt())
                activity?.recreate()
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

        private fun updateCategoryVisibility(displayMode: String) {
            findPreference<Preference>("category_floating_ball")?.isVisible = displayMode == "floating_ball"
            findPreference<Preference>("category_dynamic_island")?.isVisible = displayMode == "dynamic_island"
            findPreference<Preference>("category_simple_mode")?.isVisible = displayMode == "simple_mode"
            // AI助手分类在所有模式下都可见
            findPreference<Preference>("category_ai_assistant")?.isVisible = true
        }

        override fun onPreferenceTreeClick(preference: Preference): Boolean {
            when (preference.key) {
                "view_search_history" -> {
                    startActivity(Intent(requireContext(), SearchHistoryActivity::class.java))
                    return true
                }
                "permission_management" -> {
                    startActivity(Intent(requireContext(), PermissionManagementActivity::class.java))
                    return true
                }
                "onboarding_guide" -> {
                    startActivity(Intent(requireContext(), com.example.aifloatingball.ui.onboarding.OnboardingActivity::class.java))
                    return true
                }
                "select_apps_for_notification" -> {
                    startActivity(Intent(requireContext(), com.example.aifloatingball.settings.AppSelectionActivity::class.java))
                    return true
                }
                "test_notification" -> {
                    sendTestNotification()
                    return true
                }
                "app_search_settings" -> {
                    startActivity(Intent(requireContext(), com.example.aifloatingball.settings.AppSearchSettingsActivity::class.java))
                    return true
                }
                "search_engine_settings" -> {
                    startActivity(Intent(requireContext(), SearchEngineSettingsActivity::class.java))
                    return true
                }
                "ai_search_engine_settings" -> {
                    startActivity(Intent(requireContext(), AISearchEngineSettingsActivity::class.java))
                    return true
                }
                "menu_manager" -> {
                    startActivity(Intent(requireContext(), MenuManagerActivity::class.java))
                    return true
                }
                "master_prompt_settings" -> {
                    startActivity(Intent(requireContext(), MasterPromptSettingsActivity::class.java))
                    return true
                }
                "ai_api_settings" -> {
                    startActivity(Intent(requireContext(), AIApiSettingsActivity::class.java))
                    return true
                }
            }
            return super.onPreferenceTreeClick(preference)
        }

        private fun sendTestNotification() {
            val context = requireContext()
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
            val packageName = context.packageName

            // 1. Temporarily add this app to the notification whitelist
            val originalWhitelist = sharedPreferences.getStringSet("selected_notification_apps", null)
            val newWhitelist = originalWhitelist?.toMutableSet() ?: mutableSetOf()
            val wasInWhitelist = newWhitelist.contains(packageName)
            if (!wasInWhitelist) {
                newWhitelist.add(packageName)
                sharedPreferences.edit().putStringSet("selected_notification_apps", newWhitelist).apply()
            }
            
            // 创建测试通知
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // 创建通知渠道 (Android 8.0+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    "test_notification_channel",
                    "测试通知",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "用于测试灵动岛通知功能的通知渠道"
                }
                notificationManager.createNotificationChannel(channel)
            }
            
            // 创建通知内容
            val testMessages = listOf(
                "这是一个测试通知" to "点击可以将此内容作为搜索关键词使用",
                "微信消息" to "张三: 今天天气不错，要不要一起出去玩？",
                "淘宝购物" to "您的订单已发货，预计明天到达，请注意查收",
                "系统提醒" to "您的手机存储空间不足，建议清理缓存文件",
                "新闻推送" to "科技新闻: AI技术在移动应用中的最新应用趋势"
            )
            
            val randomMessage = testMessages.random()
            
            val notification = NotificationCompat.Builder(context, "test_notification_channel")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(randomMessage.first)
                .setContentText(randomMessage.second)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(true)
                .setTimeoutAfter(10000) // 10秒后自动消失
                .build()
            
            // 发送通知
            val notificationId = System.currentTimeMillis().toInt()
            notificationManager.notify(notificationId, notification)
            
            // 显示提示
            Toast.makeText(context, "测试通知已发送，请查看灵动岛显示效果", Toast.LENGTH_SHORT).show()

            // 2. Restore the original whitelist after a short delay
            Handler(Looper.getMainLooper()).postDelayed({
                if (!wasInWhitelist) {
                    // Only remove if it wasn't there originally
                    val currentWhitelist = sharedPreferences.getStringSet("selected_notification_apps", null)?.toMutableSet()
                    currentWhitelist?.remove(packageName)
                    sharedPreferences.edit().putStringSet("selected_notification_apps", currentWhitelist).apply()
                }
            }, 1000) // 1-second delay is enough for the notification to be processed
            
            // 延迟移除通知以模拟真实场景
            Handler(Looper.getMainLooper()).postDelayed({
                notificationManager.cancel(notificationId)
            }, 15000) // 15秒后移除
        }
    }

    // Define other fragments if they are inner classes
    class GeneralSettingsFragment : BaseSettingsFragment() {
         override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.general_preferences, rootKey)
        }
    }
     class BallSettingsFragment : BaseSettingsFragment() {
         override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.ball_preferences, rootKey)
        }
    }
     class FloatingWindowSettingsFragment : BaseSettingsFragment() {
         override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.floating_window_preferences, rootKey)
        }
    }

    class ApiSettingsFragment : BaseSettingsFragment() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.ai_api_preferences, rootKey)
        }
    }

    class WebSearchEngineManagerFragment : BaseSettingsFragment() {
        private lateinit var settingsManager: SettingsManager

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            settingsManager = SettingsManager.getInstance(requireContext())
            preferenceScreen = preferenceManager.createPreferenceScreen(requireContext())
            buildPreferences()
        }

        private fun buildPreferences() {
            preferenceScreen.removeAll()
            val context = requireContext()

            val enabledEngines = settingsManager.getEnabledSearchEngines().toMutableSet()

            val allEngines = settingsManager.getAllSearchEngines()
            val customEngines = settingsManager.getCustomSearchEngines().map { it.name }.toSet()

            // Default Engines Category
            val defaultCategory = androidx.preference.PreferenceCategory(context).apply { title = "默认搜索引擎" }
            preferenceScreen.addPreference(defaultCategory)
            allEngines.filter { !customEngines.contains(it.name) }.forEach { engine ->
                val switchPref = SwitchPreferenceCompat(context).apply {
                    key = "engine_enabled_${engine.name}"
                    title = engine.displayName
                    summary = engine.url
                    isChecked = enabledEngines.contains(engine.name)
                    setOnPreferenceChangeListener { _, newValue ->
                        if (newValue as Boolean) {
                            enabledEngines.add(engine.name)
                        } else {
                            enabledEngines.remove(engine.name)
                        }
                        settingsManager.saveEnabledSearchEngines(enabledEngines)
                        true
                    }
                }
                defaultCategory.addPreference(switchPref)
            }

            // Custom Engines Category
            val customCategory = androidx.preference.PreferenceCategory(context).apply { title = "自定义搜索引擎" }
            preferenceScreen.addPreference(customCategory)
            allEngines.filter { customEngines.contains(it.name) }.forEach { engine ->
                 val switchPref = SwitchPreferenceCompat(context).apply {
                    key = "engine_enabled_${engine.name}"
                    title = engine.displayName
                    summary = engine.url
                    isChecked = enabledEngines.contains(engine.name)
                     setOnPreferenceChangeListener { _, newValue ->
                        if (newValue as Boolean) {
                            enabledEngines.add(engine.name)
                        } else {
                            enabledEngines.remove(engine.name)
                        }
                        settingsManager.saveEnabledSearchEngines(enabledEngines)
                        true
                    }
                }
                customCategory.addPreference(switchPref)
            }

            // Add action to add new engine
            val addEnginePref = androidx.preference.Preference(context).apply {
                title = "添加新的搜索引擎"
                summary = "自定义您的搜索引擎"
                setIcon(android.R.drawable.ic_menu_add)
                setOnPreferenceClickListener {
                    android.widget.Toast.makeText(context, "此功能待实现", android.widget.Toast.LENGTH_SHORT).show()
                    true
                }
            }
            preferenceScreen.addPreference(addEnginePref)
        }
    }
} 