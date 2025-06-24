package com.example.aifloatingball

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.example.aifloatingball.service.DynamicIslandService
import com.example.aifloatingball.service.FloatingWindowService
import com.example.aifloatingball.settings.SearchEngineSelectionFragment

class SettingsActivity : AppCompatActivity(), PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

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
            // Check and start the correct service on initial launch
            updateDisplayMode()
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
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
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

    override fun onPreferenceStartFragment(caller: PreferenceFragmentCompat, pref: Preference): Boolean {
        // Instantiate the new Fragment
        val args = pref.extras
        val fragment = supportFragmentManager.fragmentFactory.instantiate(
            classLoader,
            pref.fragment!!
        )
        fragment.arguments = args
        fragment.setTargetFragment(caller, 0)
        // Replace the existing Fragment with the new Fragment
        supportFragmentManager.beginTransaction()
            .replace(R.id.settings, fragment)
            .addToBackStack(null)
            .commit()
        return true
    }
} 