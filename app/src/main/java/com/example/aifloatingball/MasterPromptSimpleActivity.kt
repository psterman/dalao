package com.example.aifloatingball

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.appbar.MaterialToolbar

class MasterPromptSimpleActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "MasterPromptSimple"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_master_prompt_settings_simple)
            
            // 设置标题栏
            val toolbar: MaterialToolbar = findViewById(R.id.toolbar)
            setSupportActionBar(toolbar)
            supportActionBar?.apply {
                title = getString(R.string.title_master_prompt_settings)
                setDisplayHomeAsUpEnabled(true)
            }

            // 加载设置Fragment
            if (savedInstanceState == null) {
                supportFragmentManager
                    .beginTransaction()
                    .replace(R.id.settings_container, MasterPromptSimpleFragment())
                    .commit()
            }
        } catch (e: Exception) {
            Log.e(TAG, "创建主提示词设置页面失败", e)
            Toast.makeText(this, "加载设置页面失败: ${e.message}", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    class MasterPromptSimpleFragment : PreferenceFragmentCompat() {
        private lateinit var settingsManager: SettingsManager

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            try {
                setPreferencesFromResource(R.xml.prompt_basic_info_preferences, rootKey)
                settingsManager = SettingsManager.getInstance(requireContext())
                
                // 这里可以添加各种设置项的监听器
            } catch (e: Exception) {
                Log.e(TAG, "创建设置Fragment失败", e)
                Toast.makeText(context, "加载设置项失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
} 