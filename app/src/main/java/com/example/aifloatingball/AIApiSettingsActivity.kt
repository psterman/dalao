package com.example.aifloatingball

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar

class AIApiSettingsActivity : AppCompatActivity() {

    private lateinit var settingsManager: SettingsManager

    private lateinit var deepSeekApiKeyEditText: EditText
    private lateinit var chatGPTApiKeyEditText: EditText
    private lateinit var saveButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_api_settings)

        settingsManager = SettingsManager.getInstance(this)

        setupToolbar()

        deepSeekApiKeyEditText = findViewById(R.id.  deepseek_api_key)
        chatGPTApiKeyEditText = findViewById(R.id.chatgpt_api_key)
        saveButton = findViewById(R.id.save_api_keys_button)

        loadApiKeys()

        saveButton.setOnClickListener {
            saveApiKeys()
            Toast.makeText(this, "API 密钥已保存", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun setupToolbar() {
        val toolbar: MaterialToolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener {
            onBackPressed()
        }
    }

    private fun loadApiKeys() {
        deepSeekApiKeyEditText.setText(settingsManager.getDeepSeekApiKey())
        chatGPTApiKeyEditText.setText(settingsManager.getChatGPTApiKey())
    }

    private fun saveApiKeys() {
        settingsManager.setDeepSeekApiKey(deepSeekApiKeyEditText.text.toString().trim())
        settingsManager.setChatGPTApiKey(chatGPTApiKeyEditText.text.toString().trim())
    }
}