package com.example.aifloatingball

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.adapter.PresetAIAdapter
import com.example.aifloatingball.model.ChatContact
import com.example.aifloatingball.model.ContactType
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class AddAIContactActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "AddAIContactActivity"
        const val EXTRA_AI_CONTACT = "extra_ai_contact"
    }

    private lateinit var searchInput: TextInputEditText
    private lateinit var searchButton: ImageButton
    private lateinit var backButton: ImageButton
    private lateinit var saveButton: ImageButton
    private lateinit var presetAIList: RecyclerView
    private lateinit var customNameInput: TextInputEditText
    private lateinit var customDescriptionInput: TextInputEditText
    private lateinit var customApiKeyInput: TextInputEditText
    private lateinit var customApiUrlInput: TextInputEditText
    private lateinit var addCustomButton: MaterialButton

    private lateinit var presetAIAdapter: PresetAIAdapter
    private var allPresetAIs = mutableListOf<PresetAI>()
    private var filteredPresetAIs = mutableListOf<PresetAI>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_ai_contact)

        initializeViews()
        setupPresetAIs()
        setupListeners()
    }

    private fun initializeViews() {
        searchInput = findViewById(R.id.search_input)
        searchButton = findViewById(R.id.search_button)
        backButton = findViewById(R.id.back_button)
        saveButton = findViewById(R.id.save_button)
        presetAIList = findViewById(R.id.preset_ai_list)
        customNameInput = findViewById(R.id.custom_name_input)
        customDescriptionInput = findViewById(R.id.custom_description_input)
        customApiKeyInput = findViewById(R.id.custom_api_key_input)
        customApiUrlInput = findViewById(R.id.custom_api_url_input)
        addCustomButton = findViewById(R.id.add_custom_button)

        // 设置RecyclerView
        presetAIAdapter = PresetAIAdapter(
            onAIClick = { presetAI ->
                addPresetAIToContacts(presetAI)
            }
        )
        presetAIList.layoutManager = LinearLayoutManager(this)
        presetAIList.adapter = presetAIAdapter
    }

    private fun setupPresetAIs() {
        // 预设AI助手列表
        allPresetAIs = mutableListOf(
            PresetAI("ChatGPT", "OpenAI的AI助手，擅长对话和文本生成", "https://api.openai.com/v1/chat/completions"),
            PresetAI("Claude", "Anthropic的AI助手，擅长分析和推理", "https://api.anthropic.com/v1/messages"),
            PresetAI("Gemini", "Google的AI助手，多模态AI模型", "https://generativelanguage.googleapis.com/v1beta/models"),
            PresetAI("文心一言", "百度的大语言模型", "https://aip.baidubce.com/rpc/2.0/ai_custom/v1/wenxinworkshop/chat"),
            PresetAI("通义千问", "阿里巴巴的大语言模型", "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation"),
            PresetAI("讯飞星火", "科大讯飞的大语言模型", "https://spark-api.xf-yun.com/v1.1/chat"),
            PresetAI("智谱清言", "智谱AI的大语言模型", "https://open.bigmodel.cn/api/paas/v3/model-api"),
            PresetAI("月之暗面", "月之暗面的大语言模型", "https://api.moonshot.cn/v1/chat/completions")
        )
        filteredPresetAIs = allPresetAIs.toMutableList()
        presetAIAdapter.updateAIs(filteredPresetAIs)
    }

    private fun setupListeners() {
        // 返回按钮
        backButton.setOnClickListener {
            finish()
        }

        // 保存按钮
        saveButton.setOnClickListener {
            // 这里可以保存当前选择的状态
            Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show()
        }

        // 搜索功能
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                performSearch(s?.toString() ?: "")
            }
        })

        searchButton.setOnClickListener {
            performSearch(searchInput.text?.toString() ?: "")
        }

        // 添加自定义AI按钮
        addCustomButton.setOnClickListener {
            addCustomAIToContacts()
        }
    }

    private fun performSearch(query: String) {
        if (query.isEmpty()) {
            filteredPresetAIs = allPresetAIs.toMutableList()
        } else {
            filteredPresetAIs = allPresetAIs.filter { ai ->
                ai.name.contains(query, ignoreCase = true) ||
                ai.description.contains(query, ignoreCase = true)
            }.toMutableList()
        }
        presetAIAdapter.updateAIs(filteredPresetAIs)
    }

    private fun addPresetAIToContacts(presetAI: PresetAI) {
        val contact = ChatContact(
            id = "ai_${System.currentTimeMillis()}",
            name = presetAI.name,
            type = ContactType.AI,
            description = presetAI.description,
            isOnline = true,
            customData = mapOf("api_url" to presetAI.apiUrl)
        )

        // 返回结果给SimpleModeActivity
        val intent = Intent()
        intent.putExtra(EXTRA_AI_CONTACT, contact)
        setResult(RESULT_OK, intent)
        
        Toast.makeText(this, "已添加 ${presetAI.name}", Toast.LENGTH_SHORT).show()
        Log.d(TAG, "添加预设AI: ${presetAI.name}")
    }

    private fun addCustomAIToContacts() {
        val name = customNameInput.text?.toString()?.trim()
        val description = customDescriptionInput.text?.toString()?.trim()
        val apiKey = customApiKeyInput.text?.toString()?.trim()
        val apiUrl = customApiUrlInput.text?.toString()?.trim()

        if (name.isNullOrEmpty()) {
            Toast.makeText(this, "请输入AI助手名称", Toast.LENGTH_SHORT).show()
            return
        }

        if (apiKey.isNullOrEmpty()) {
            Toast.makeText(this, "请输入API密钥", Toast.LENGTH_SHORT).show()
            return
        }

        if (apiUrl.isNullOrEmpty()) {
            Toast.makeText(this, "请输入API地址", Toast.LENGTH_SHORT).show()
            return
        }

        val contact = ChatContact(
            id = "ai_custom_${System.currentTimeMillis()}",
            name = name,
            type = ContactType.AI,
            description = description ?: "自定义AI助手",
            isOnline = true,
            customData = mapOf(
                "api_key" to apiKey,
                "api_url" to apiUrl
            )
        )

        // 返回结果给SimpleModeActivity
        val intent = Intent()
        intent.putExtra(EXTRA_AI_CONTACT, contact)
        setResult(RESULT_OK, intent)

        Toast.makeText(this, "已添加自定义AI助手", Toast.LENGTH_SHORT).show()
        Log.d(TAG, "添加自定义AI: $name")
    }

    /**
     * 预设AI助手数据类
     */
    data class PresetAI(
        val name: String,
        val description: String,
        val apiUrl: String
    )
} 