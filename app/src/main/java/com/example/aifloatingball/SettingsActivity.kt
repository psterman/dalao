package com.example.aifloatingball

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.*
import android.view.MenuItem

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

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    companion object {
        const val ACTION_SETTINGS_CHANGED = "com.example.aifloatingball.SETTINGS_CHANGED"
        const val EXTRA_LEFT_HANDED_MODE_CHANGED = "left_handed_mode_changed"
        const val PREF_LEFT_HANDED_MODE = "left_handed_mode"
        const val PREF_DEFAULT_SEARCH_ENGINE = "default_search_engine"
        const val PREF_DEFAULT_SEARCH_MODE = "default_search_mode"
        const val KEY_DEFAULT_SEARCH_MODE = "default_search_mode"
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)

            // 添加菜单管理入口
            findPreference<Preference>("menu_manager")?.setOnPreferenceClickListener {
                startActivity(Intent(requireContext(), MenuManagerActivity::class.java))
                true
            }
        }
    }
} 