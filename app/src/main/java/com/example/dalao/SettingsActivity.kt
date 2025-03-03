package com.example.dalao

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceFragmentCompat
import com.example.aifloatingball.R

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
        // 设置标题栏返回按钮
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        // 加载设置Fragment
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, SettingsFragment())
                .commit()
        }
    }
    
    // 处理返回按钮点击
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
    
    // 设置Fragment类
    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)
        }
    }
}